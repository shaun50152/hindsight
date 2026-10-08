package dev.hindsight.decision.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.decision.messaging.DecisionTopics;
import dev.hindsight.decision.testsupport.ShadowSidecar;
import dev.hindsight.decision.messaging.OutboxRelay;
import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.testsupport.IntegrationTestBase;
import dev.hindsight.decision.testsupport.LifecycleEventPublisher;
import dev.hindsight.decision.testsupport.TestPolicyYaml;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Invariant 6: production path unchanged under shadow load/failure/lag.
 *
 * <p>Documented p99 tolerance: baseline p99 + {@value #P99_TOLERANCE_MS} ms.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ShadowIsolationIT extends IntegrationTestBase {

    /** Allowed p99 regression vs baseline when shadow is running, failing, or lagging. */
    static final long P99_TOLERANCE_MS = 25;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int REQUEST_COUNT = 15;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    OutboxRelay outboxRelay;

    @Autowired
    dev.hindsight.decision.cache.PolicyCache policyCache;

    private ConfigurableApplicationContext shadowContext;

    @DynamicPropertySource
    static void relayOn(DynamicPropertyRegistry registry) {
        registry.add("hindsight.outbox.relay-enabled", () -> "true");
    }

    @BeforeEach
    void publishPolicies() throws Exception {
        String policyId = "shadow-iso-policy";
        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 1, TestPolicyYaml.minimal(policyId, 1), "ACTIVE", null, "ops");
        LifecycleEventPublisher.publish(
                kafkaTemplate,
                policyId,
                2,
                TestPolicyYaml.canaryMarker(policyId, 2),
                "SHADOW",
                null,
                "ops");
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> policyCache.activeContentHash(policyId) != null);
    }

    @AfterEach
    void stopShadow() {
        if (shadowContext != null) {
            shadowContext.close();
            shadowContext = null;
        }
    }

    @Test
    void productionUnchangedWithShadowRunning() throws Exception {
        ProductionRun baseline = productionRun("baseline");
        startShadowProcess(false);
        ProductionRun withShadow = productionRun("with-shadow");
        assertProductionEquivalent(baseline, withShadow);
    }

    @Test
    void productionUnchangedWithShadowCrashing() throws Exception {
        ProductionRun baseline = productionRun("baseline-crash");
        startShadowProcess(true);
        ProductionRun withCrash = productionRun("with-crash");
        assertProductionEquivalent(baseline, withCrash);
    }

    @Test
    void productionUnchangedWithShadowLag() throws Exception {
        seedDecisionMadeBacklog(40);
        ProductionRun baseline = productionRun("baseline-lag");
        startShadowProcess(false);
        ProductionRun withLag = productionRun("with-lag");
        assertProductionEquivalent(baseline, withLag);
    }

    private void startShadowProcess(boolean crash) {
        shadowContext = ShadowSidecar.start(crash);
    }

    private ProductionRun productionRun(String prefix) throws Exception {
        List<String> responseSignatures = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        List<String> madeSignatures = new ArrayList<>();
        for (int i = 0; i < REQUEST_COUNT; i++) {
            long start = System.nanoTime();
            MvcResult result = mockMvc.perform(post("/v1/decisions")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + DecisionRoles.DECIDER)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decisionBody(prefix + "-req-" + i, prefix + "-cust-" + i)))
                    .andExpect(status().isOk())
                    .andReturn();
            latencies.add((System.nanoTime() - start) / 1_000_000L);
            JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
            responseSignatures.add(node.get("outcome").asString() + "|" + node.get("contentHash").asString());
            outboxRelay.relay();
        }
        madeSignatures.addAll(collectDecisionMadeSignatures(prefix));
        latencies.sort(Comparator.naturalOrder());
        long p99 = latencies.get((int) Math.ceil(0.99 * latencies.size()) - 1);
        return new ProductionRun(responseSignatures, madeSignatures, p99);
    }

    private List<String> collectDecisionMadeSignatures(String prefix) {
        var consumerProps = KafkaTestUtils.consumerProps(kafkaBootstrap(), "shadow-iso-" + prefix, "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var consumer = new DefaultKafkaConsumerFactory<>(
                        consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(DecisionTopics.DECISION_MADE));
        List<String> sigs = new ArrayList<>();
        for (int attempt = 0; attempt < 30 && sigs.size() < REQUEST_COUNT; attempt++) {
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(300));
            for (ConsumerRecord<String, String> record : records) {
                if (record.key() != null && record.key().startsWith(prefix + "-cust-")) {
                    JsonNode root = JSON.readTree(record.value());
                    JsonNode payload = root.get("payload");
                    sigs.add(payload.get("outcome").asString() + "|" + payload.get("contentHash").asString());
                }
            }
        }
        consumer.close();
        return sigs;
    }

    private void seedDecisionMadeBacklog(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            mockMvc.perform(post("/v1/decisions")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + DecisionRoles.DECIDER)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(decisionBody("lag-seed-" + i, "lag-seed-cust-" + i)))
                    .andExpect(status().isOk());
            outboxRelay.relay();
        }
    }

    private void assertProductionEquivalent(ProductionRun baseline, ProductionRun underTest) {
        assertThat(underTest.responseSignatures()).isEqualTo(baseline.responseSignatures());
        assertThat(underTest.madeSignatures()).hasSameSizeAs(baseline.madeSignatures());
        assertThat(underTest.p99LatencyMs()).isLessThanOrEqualTo(baseline.p99LatencyMs() + P99_TOLERANCE_MS);
    }

    private static String decisionBody(String requestId, String customerId) {
        return """
                {
                  "requestId": "%s",
                  "policyId": "shadow-iso-policy",
                  "applicant": {
                    "customerId": "%s",
                    "currentLimit": 5000,
                    "requestedIncrease": 500,
                    "utilization": 0.3,
                    "delinquencies12m": 0,
                    "tenureMonths": 24,
                    "ficoBand": 4,
                    "incomeBand": "M",
                    "incomeVerified": true,
                    "segment": "A",
                    "asOf": "%s"
                  }
                }
                """
                .formatted(requestId, customerId, Instant.now());
    }

    private record ProductionRun(List<String> responseSignatures, List<String> madeSignatures, long p99LatencyMs) {}
}

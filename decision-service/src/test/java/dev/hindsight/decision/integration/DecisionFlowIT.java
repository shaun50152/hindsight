package dev.hindsight.decision.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.decision.messaging.DecisionTopics;
import dev.hindsight.decision.messaging.OutboxRelay;
import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.testsupport.IntegrationTestBase;
import dev.hindsight.decision.testsupport.LifecycleEventPublisher;
import dev.hindsight.decision.testsupport.TestPolicyYaml;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DecisionFlowIT extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    OutboxRelay outboxRelay;

    @Autowired
    dev.hindsight.decision.cache.PolicyCache policyCache;

    @BeforeEach
    void publishActivePolicy() throws Exception {
        String policyId = "credit-line-increase";
        String yaml = TestPolicyYaml.minimal(policyId, 1);
        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 1, yaml, "ACTIVE", null, "ops");
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> policyCache.activeContentHash(policyId) != null);
    }

    @Test
    void postDecisionPublishesDecisionMade() throws Exception {
        String body =
                """
                {
                  "requestId": "req-flow-1",
                  "applicant": {
                    "customerId": "cust-flow-1",
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
                        .formatted(Instant.now());

        mockMvc.perform(post("/v1/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + DecisionRoles.DECIDER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var consumerProps = KafkaTestUtils.consumerProps(kafkaBootstrap(), "decision-flow", "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var consumer = new DefaultKafkaConsumerFactory<>(
                        consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(DecisionTopics.DECISION_MADE));

        ConsumerRecord<String, String> match = null;
        for (int i = 0; i < 40 && match == null; i++) {
            outboxRelay.relay();
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
            for (var record : records) {
                if ("cust-flow-1".equals(record.key())) {
                    match = record;
                    break;
                }
            }
        }
        consumer.close();
        assertThat(match).isNotNull();
        assertThat(match.value()).contains("req-flow-1");
    }

}

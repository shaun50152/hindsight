package dev.hindsight.simulation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import dev.hindsight.simulation.backtest.BacktestJobWatcher;
import dev.hindsight.simulation.backtest.BacktestStatus;
import dev.hindsight.simulation.security.SimulationRoles;
import dev.hindsight.simulation.testsupport.SimulationPostgresTestSupport;
import dev.hindsight.simulation.testsupport.TestJwt;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** Invariant 9: backtesting the active policy over history yields zero flips. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BacktestEquivalenceIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String POLICY_YAML =
            """
            policyId: credit-line-increase
            version: 1
            description: invariant9
            inputs: applicant
            defaultOutcome: REFER
            rules:
              - id: R-approve
                when: applicant.ficoBand >= 3 && applicant.utilization < 0.5
                outcome: APPROVE
                reason: RC_STRONG
                maxIncrease: "1000"
              - id: R-decline
                when: applicant.delinquencies12m >= 2
                outcome: DECLINE
                reason: RC_DELINQ
            """;

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        SimulationPostgresTestSupport.registerSchemas(postgres, registry);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "simulation-service-test");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("hindsight.jwt.hmac-secret", () -> TestJwt.SECRET);
        registry.add("hindsight.backtest.runner", () -> "local");
        registry.add("hindsight.backtest.shard-count", () -> "1");
        registry.add("hindsight.outbox.relay-enabled", () -> "true");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    BacktestJobWatcher backtestJobWatcher;

    @Autowired
    @Qualifier("policyDataSource")
    DataSource policyDataSource;

    String contentHash;

    @BeforeEach
    void seedPolicyAndHistory() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(policyDataSource);
        var policy = PolicyYamlParser.parse(POLICY_YAML).policy();
        contentHash = PolicyContentHash.hash(policy);
        jdbcTemplate.update(
                """
                INSERT INTO policies (policy_id, version, content_hash, yaml, status, author_id, created_at)
                VALUES ('credit-line-increase', 1, ?, ?, 'ACTIVE', 'author', now())
                """,
                contentHash,
                POLICY_YAML);

        publishDecision("cust-1", "APPROVE", 3, 0.4, 0);
        publishDecision("cust-2", "DECLINE", 2, 0.9, 2);
        publishDecision("cust-3", "REFER", 2, 0.6, 0);
    }

    @Test
    void activePolicyBacktestHasZeroFlips() throws Exception {
        String body =
                """
                {"candidateContentHash":"%s","partitions":[0],"shardCount":1}
                """
                        .formatted(contentHash);
        String response = mockMvc.perform(post("/v1/backtests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SimulationRoles.OPS))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID backtestId = UUID.fromString(JSON.readTree(response).get("id").asString());

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    backtestJobWatcher.poll();
                    return BacktestStatus.COMPLETE.name()
                            .equals(JSON.readTree(mockMvc.perform(get("/v1/backtests/" + backtestId)
                                            .with(jwt()
                                                    .authorities(new SimpleGrantedAuthority(
                                                            "ROLE_" + SimulationRoles.AUDITOR))))
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString())
                            .get("status")
                            .asString());
                });

        String reportJson = mockMvc.perform(get("/v1/backtests/" + backtestId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + SimulationRoles.AUDITOR))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(JSON.readTree(reportJson).get("reportJson").asString()).contains("\"flipCount\": 0");
    }

    private void publishDecision(
            String customerId, String outcome, int fico, double utilization, int delinq) throws Exception {
        DecisionMadePayload payload = new DecisionMadePayload(
                UUID.randomUUID().toString(),
                "req-" + customerId,
                "credit-line-increase",
                1,
                contentHash,
                outcome,
                List.of(),
                null,
                List.of(),
                new DecisionMadePayload.ApplicantSnapshotPayload(
                        customerId, 5000, 1000, utilization, delinq, 24, fico, "M", true, "A", Instant.now()),
                DecisionMadePayload.CURRENT_SCHEMA_VERSION,
                1L,
                null);
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(), "decision.made", Instant.now(), customerId, JSON.valueToTree(payload));
        kafkaTemplate.send("decision.made", 0, customerId, JSON.writeValueAsString(envelope)).get();
    }
}

package dev.hindsight.policy.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hindsight.common.events.BacktestCompletedPayload;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.common.events.SimulationTopics;
import dev.hindsight.policy.lifecycle.PolicyStatus;
import dev.hindsight.policy.service.PolicyConflictException;
import dev.hindsight.policy.persistence.PolicyRepository;
import dev.hindsight.policy.service.PolicyService;
import dev.hindsight.policy.testsupport.PostgresTestSupport;
import dev.hindsight.policy.testsupport.TestPolicyYaml;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Promotion gate (ADR 0013): CANARY/ACTIVE require COMPLETE backtest report for content hash; SHADOW does not.
 */
@SpringBootTest
@Testcontainers
class BacktestPromotionGateIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerPolicySchema(postgres, registry);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "policy-service-test");
        registry.add("hindsight.jwt.hmac-secret", () -> "test-jwt-hmac-secret-32bytes-min!!");
        registry.add("hindsight.outbox.relay-enabled", () -> "false");
    }

    @Autowired
    PolicyService policyService;

    @Autowired
    PolicyRepository policyRepository;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void shadowWithoutReportAllowed_canaryAndActiveRequireCompleteReport() throws Exception {
        String policyId = "gate-policy";
        String yaml = TestPolicyYaml.minimal(policyId, 1);
        policyService.createDraft(policyId, yaml, "author-1");
        policyService.submit(policyId, 1, "author-1");
        var approved = policyService.approve(policyId, 1, "checker-1");
        String hash = approved.contentHash();

        policyService.promote(policyId, 1, PolicyStatus.SHADOW, null, "ops-1");

        assertThatThrownBy(() -> policyService.promote(policyId, 1, PolicyStatus.CANARY, 10, "ops-1"))
                .isInstanceOf(PolicyConflictException.class)
                .hasMessageContaining("COMPLETE backtest report");

        publishBacktestCompleted(hash, BacktestCompletedPayload.BacktestStatus.INCOMPLETE);
        awaitReportStatus(hash, "INCOMPLETE");
        assertThatThrownBy(() -> policyService.promote(policyId, 1, PolicyStatus.CANARY, 10, "ops-1"))
                .isInstanceOf(PolicyConflictException.class);

        publishBacktestCompleted(hash, BacktestCompletedPayload.BacktestStatus.COMPLETE);
        awaitReportStatus(hash, "COMPLETE");

        policyService.promote(policyId, 1, PolicyStatus.CANARY, 10, "ops-1");
        policyService.promote(policyId, 1, PolicyStatus.ACTIVE, null, "ops-1");
        assertThat(policyRepository.find(policyId, 1).orElseThrow().status()).isEqualTo(PolicyStatus.ACTIVE);
    }

    private void awaitReportStatus(String contentHash, String status) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> status.equals(jdbcTemplate.queryForObject(
                        "SELECT status FROM backtest_reports WHERE content_hash = ?", String.class, contentHash)));
    }

    private void publishBacktestCompleted(String contentHash, BacktestCompletedPayload.BacktestStatus status)
            throws Exception {
        BacktestCompletedPayload payload = new BacktestCompletedPayload(
                UUID.randomUUID().toString(), contentHash, status, Map.of("flipCount", 0));
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                SimulationTopics.BACKTEST_COMPLETED,
                Instant.now(),
                contentHash,
                JSON.valueToTree(payload));
        kafkaTemplate.send(SimulationTopics.BACKTEST_COMPLETED, contentHash, JSON.writeValueAsString(envelope)).get();
    }
}

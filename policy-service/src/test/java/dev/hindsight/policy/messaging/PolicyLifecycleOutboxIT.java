package dev.hindsight.policy.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.policy.lifecycle.PolicyStatus;
import dev.hindsight.policy.testsupport.PostgresTestSupport;
import dev.hindsight.policy.testsupport.TestPolicyYaml;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Testcontainers
class PolicyLifecycleOutboxIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerPolicySchema(postgres, registry);
        registry.add("hindsight.kafka.enabled", () -> "false");
        registry.add("hindsight.jwt.hmac-secret", () -> "test-jwt-hmac-secret-32bytes-min!!");
    }

    @Autowired
    dev.hindsight.policy.service.PolicyService policyService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void promoteRollbackAndRestoreIncludeYamlInOutbox() throws Exception {
        String policyId = "lifecycle-yaml-policy";
        policyService.createDraft(policyId, TestPolicyYaml.minimal(policyId, 1), "maker-1");
        policyService.submit(policyId, 1, "maker-1");
        policyService.approve(policyId, 1, "checker-1");
        promoteToActive(policyId, 1, "ops-1");

        policyService.createDraft(policyId, TestPolicyYaml.minimal(policyId, 2), "maker-1");
        policyService.submit(policyId, 2, "maker-1");
        policyService.approve(policyId, 2, "checker-2");
        promoteToActive(policyId, 2, "ops-1");

        policyService.rollback(policyId, 2, "ops-1");

        var rows = jdbcTemplate.queryForList(
                "SELECT message_key, payload::text AS payload FROM outbox WHERE topic = ? ORDER BY created_at",
                OutboxWriter.POLICY_LIFECYCLE_TOPIC);
        assertThat(rows).isNotEmpty();
        for (var row : rows) {
            String key = (String) row.get("message_key");
            assertThat(key).contains(":");
            PolicyLifecycleEvent event = JSON.readValue((String) row.get("payload"), PolicyLifecycleEvent.class);
            assertThat(event.yaml()).isNotBlank();
            assertThat(key).isEqualTo(PolicyLifecycleEvent.messageKey(event.policyId(), event.version()));
        }

        var restored = rows.stream()
                .map(r -> {
                    try {
                        return JSON.readValue((String) r.get("payload"), PolicyLifecycleEvent.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(e -> e.version() == 1 && "ACTIVE".equals(e.status()))
                .findFirst();
        assertThat(restored).isPresent();
        assertThat(restored.get().yaml()).contains("policyId: " + policyId);
    }

    private void promoteToActive(String policyId, int version, String actor) {
        policyService.promote(policyId, version, PolicyStatus.SHADOW, null, actor);
        policyService.promote(policyId, version, PolicyStatus.CANARY, 100, actor);
        policyService.promote(policyId, version, PolicyStatus.ACTIVE, null, actor);
    }
}

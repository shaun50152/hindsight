package dev.hindsight.policy.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hindsight.policy.testsupport.PostgresTestSupport;
import dev.hindsight.policy.testsupport.TestPolicyYaml;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PolicyImmutabilityTriggerIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerPolicySchema(postgres, registry);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PolicyRepository policyRepository;

    @Test
    void approvedRowRejectsYamlOrHashUpdateAndDelete() {
        String policyId = "immutable-policy";
        PolicyRecord record = new PolicyRecord(
                policyId,
                1,
                "deadbeef",
                TestPolicyYaml.minimal(policyId, 1),
                dev.hindsight.policy.lifecycle.PolicyStatus.APPROVED,
                "author",
                java.util.Optional.of("checker"),
                java.util.Optional.empty(),
                java.time.Instant.now(),
                java.util.Optional.of(java.time.Instant.now()));
        policyRepository.insert(record);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE policies SET yaml = 'changed' WHERE policy_id = ? AND version = 1", policyId))
                .hasMessageContaining("immutable");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE policies SET content_hash = 'other' WHERE policy_id = ? AND version = 1", policyId))
                .hasMessageContaining("immutable");

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM policies WHERE policy_id = ? AND version = 1", policyId))
                .hasMessageContaining("immutable");
    }
}

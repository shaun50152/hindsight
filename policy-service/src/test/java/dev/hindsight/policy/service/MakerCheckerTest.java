package dev.hindsight.policy.service;

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
class MakerCheckerTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerPolicySchema(postgres, registry);
    }

    @Autowired
    PolicyService policyService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void authorCannotApproveOwnVersion() {
        String policyId = "maker-checker-policy";
        var draft = policyService.createDraft(policyId, TestPolicyYaml.minimal(policyId, 1), "author-1");
        policyService.submit(policyId, draft.version(), "author-1");
        assertThatThrownBy(() -> policyService.approve(policyId, draft.version(), "author-1"))
                .isInstanceOf(PolicyConflictException.class);
    }

    @Test
    void databaseRejectsSameAuthorAndApproverOnInsert() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO policies (policy_id, version, content_hash, yaml, status, author_id, approver_id)
                        VALUES ('db-check', 1, 'abc', 'yaml', 'IN_REVIEW', 'same', 'same')
                        """))
                .hasMessageContaining("maker_checker");
    }
}

package dev.hindsight.audit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.audit.chain.AuditAppender;
import dev.hindsight.audit.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@TestPropertySource(properties = "hindsight.audit.checkpoint-interval=2")
class AuditCheckpointIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    AuditAppender auditAppender;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void writesCheckpointEveryNRecords() {
        jdbcTemplate.execute("TRUNCATE audit_log, audit_checkpoints RESTART IDENTITY");
        auditAppender.append("cp1", "test", JSON.readTree("{\"n\":1}"));
        auditAppender.append("cp2", "test", JSON.readTree("{\"n\":2}"));
        Long checkpoints = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_checkpoints WHERE chain_id = 'main'", Long.class);
        assertThat(checkpoints).isEqualTo(1);
    }
}

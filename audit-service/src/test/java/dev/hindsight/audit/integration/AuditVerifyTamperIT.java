package dev.hindsight.audit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.audit.chain.AuditAppender;
import dev.hindsight.audit.security.AuditRoles;
import dev.hindsight.audit.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuditVerifyTamperIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuditAppender auditAppender;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedChain() {
        jdbcTemplate.execute("TRUNCATE audit_log, audit_checkpoints RESTART IDENTITY");
        auditAppender.append("e1", "test", JSON.readTree("{\"a\":1}"));
        auditAppender.append("e2", "test", JSON.readTree("{\"a\":2}"));
        auditAppender.append("e3", "test", JSON.readTree("{\"a\":3}"));
    }

    @Test
    void detectsPayloadTamper() throws Exception {
        jdbcTemplate.update("UPDATE audit_log SET payload = '{\"a\":999}'::jsonb WHERE seq = 2");
        assertBroken(2L, "HASH_MISMATCH");
    }

    @Test
    void detectsHashTamper() throws Exception {
        jdbcTemplate.update("UPDATE audit_log SET hash = ? WHERE seq = 2", "f".repeat(64));
        assertBroken(2L, "HASH_MISMATCH");
    }

    @Test
    void detectsPrevHashTamper() throws Exception {
        jdbcTemplate.update("UPDATE audit_log SET prev_hash = ? WHERE seq = 2", "f".repeat(64));
        assertBroken(2L, "PREV_HASH_MISMATCH");
    }

    @Test
    void detectsDeletedRowGap() throws Exception {
        jdbcTemplate.update("DELETE FROM audit_log WHERE seq = 2");
        assertBroken(2L, "GAP");
    }

    private void assertBroken(long seq, String reason) throws Exception {
        var result = mockMvc.perform(get("/v1/audit/verify")
                        .param("from", "1")
                        .param("to", "3")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + AuditRoles.AUDITOR))))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("BROKEN");
        assertThat(body).contains(String.valueOf(seq));
        assertThat(body).contains(reason);
    }
}

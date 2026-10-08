package dev.hindsight.audit.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hindsight.audit.testsupport.IntegrationTestBase;
import dev.hindsight.audit.testsupport.SharedTestcontainers;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AuditDbPermissionsIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void appRoleCannotUpdateOrDelete() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO audit_log (chain_id, seq, event_id, event_type, payload, prev_hash, hash) VALUES ('main', 99, 'perm-test', 't', '{}'::jsonb, ?, ?)",
                "0".repeat(64),
                "1".repeat(64));

        String url = SharedTestcontainers.POSTGRES.getJdbcUrl() + "?currentSchema=audit";
        try (Connection conn = DriverManager.getConnection(url, "hindsight_audit_app", "audit-app-test-password")) {
            conn.createStatement().execute("SET search_path TO audit");
            assertThatThrownBy(() -> conn.createStatement().executeUpdate("DELETE FROM audit_log WHERE seq = 99"))
                    .hasMessageContaining("permission denied");
            assertThatThrownBy(() -> conn.createStatement().executeUpdate("UPDATE audit_log SET hash = 'x' WHERE seq = 99"))
                    .hasMessageContaining("permission denied");
        }
    }
}

package dev.hindsight.simulation.policy;

import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PolicyLookupService {

    private final JdbcTemplate jdbc;

    public PolicyLookupService(@Qualifier("policyDataSource") DataSource policyDataSource) {
        this.jdbc = new JdbcTemplate(policyDataSource);
    }

    public Optional<PolicyRef> findByContentHash(String contentHash) {
        return jdbc.query(
                        """
                        SELECT policy_id, version, yaml FROM policies WHERE content_hash = ? LIMIT 1
                        """,
                        (rs, rowNum) -> new PolicyRef(
                                rs.getString("policy_id"), rs.getInt("version"), rs.getString("yaml")),
                        contentHash)
                .stream()
                .findFirst();
    }

    public record PolicyRef(String policyId, int version, String yaml) {}
}

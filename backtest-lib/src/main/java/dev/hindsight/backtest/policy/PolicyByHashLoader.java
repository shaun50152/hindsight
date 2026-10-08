package dev.hindsight.backtest.policy;

import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PolicyByHashLoader {

    private PolicyByHashLoader() {}

    public static CompiledPolicy loadFromPolicySchema(DataSource policyDataSource, String contentHash) {
        JdbcTemplate jdbc = new JdbcTemplate(policyDataSource);
        List<String> rows = jdbc.query(
                "SELECT yaml FROM policies WHERE content_hash = ? LIMIT 1",
                (rs, rowNum) -> rs.getString("yaml"),
                contentHash);
        if (rows.isEmpty()) {
            throw new PolicyLoadException("No policy found for contentHash " + contentHash);
        }
        return compileVerified(rows.getFirst(), contentHash);
    }

    public static CompiledPolicy compileVerified(String yaml, String expectedHash) {
        PolicyYamlParser.ParseResult parsed = PolicyYamlParser.parse(yaml);
        if (!parsed.structureErrors().isEmpty()) {
            throw new PolicyLoadException("Invalid policy yaml: " + parsed.structureErrors());
        }
        Policy policy = parsed.policy();
        String hash = PolicyContentHash.hash(policy);
        if (!hash.equals(expectedHash)) {
            throw new PolicyLoadException("contentHash mismatch expected=" + expectedHash + " computed=" + hash);
        }
        return PolicyCompiler.compileValidatedPolicy(policy)
                .compiled()
                .orElseThrow(() -> new PolicyLoadException("Policy compile failed"));
    }

    public static final class PolicyLoadException extends RuntimeException {
        public PolicyLoadException(String message) {
            super(message);
        }
    }
}

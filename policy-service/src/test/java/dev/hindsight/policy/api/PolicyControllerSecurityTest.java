package dev.hindsight.policy.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.policy.testsupport.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class PolicyControllerSecurityTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerPolicySchema(postgres, registry);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanPolicies() {
        jdbcTemplate.update("DELETE FROM outbox");
        jdbcTemplate.update("DELETE FROM policy_events");
        jdbcTemplate.update("DELETE FROM policies");
    }

    @ParameterizedTest
    @CsvSource({
        "/v1/policies/p/drafts,POST,MAKER,201",
        "/v1/policies/p/drafts,POST,CHECKER,403",
        "/v1/policies/missing/versions/99/submit,POST,MAKER,404",
        "/v1/policies/missing/versions/99/approve,POST,CHECKER,404",
        "/v1/policies/missing/versions/99/reject,POST,CHECKER,404",
        "/v1/policies/missing/versions/99/promote,POST,OPS,404",
        "/v1/policies/missing/versions/99/rollback,POST,OPS,404",
        "/v1/policies/p/versions,GET,AUDITOR,200",
        "/v1/policies/missing/versions/99,GET,AUDITOR,404",
        "/v1/policies/missing/versions/1/diff/2,GET,AUDITOR,404"
    })
    void roleMatrix(String path, String method, String role, int expectedStatus) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(path));
            case "GET" -> get(path);
            default -> throw new IllegalArgumentException(method);
        };
        var authenticated = builder.with(
                jwt().jwt(j -> j.subject("user-1")).authorities(new SimpleGrantedAuthority("ROLE_" + role)));
        mockMvc.perform(authenticated).andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @CsvSource({
        "/v1/policies/p/drafts,POST",
        "/v1/policies/p/versions,GET"
    })
    void unauthenticatedReturns401(String path, String method) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody(path));
            case "GET" -> get(path);
            default -> throw new IllegalArgumentException(method);
        };
        mockMvc.perform(builder).andExpect(status().isUnauthorized());
    }

    private static String postBody(String path) {
        if (path.endsWith("/drafts")) {
            return """
                    {"yaml":"policyId: p\\nversion: 1\\ndescription: d\\ninputs: applicant\\ndefaultOutcome: REFER\\nrules:\\n  - id: R1\\n    when: \\"applicant.ficoBand >= 1\\"\\n    outcome: APPROVE\\n    reason: RC_STRONG\\n"}\
                    """;
        }
        if (path.endsWith("/promote")) {
            return "{\"target\":\"SHADOW\"}";
        }
        return "{}";
    }
}

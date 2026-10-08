package dev.hindsight.audit.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.audit.security.AuditRoles;
import dev.hindsight.audit.testsupport.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerSecurityTest extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Test
    void auditorCanVerify() throws Exception {
        mockMvc.perform(get("/v1/audit/verify")
                        .param("from", "1")
                        .param("to", "1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + AuditRoles.AUDITOR))))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedVerifyReturns401() throws Exception {
        mockMvc.perform(get("/v1/audit/verify").param("from", "1").param("to", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAuditorReplayForbidden() throws Exception {
        mockMvc.perform(post("/v1/audit/replay/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DECIDER"))))
                .andExpect(status().isForbidden());
    }
}

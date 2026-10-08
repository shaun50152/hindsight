package dev.hindsight.decision.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.testsupport.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FailClosedIT extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returns503WhenNoActivePolicy() throws Exception {
        String body =
                """
                {
                  "requestId": "req-fail-closed",
                  "policyId": "unknown-policy",
                  "applicant": {
                    "customerId": "cust-1",
                    "currentLimit": 5000,
                    "requestedIncrease": 500,
                    "utilization": 0.3,
                    "delinquencies12m": 0,
                    "tenureMonths": 24,
                    "ficoBand": 4,
                    "incomeBand": "M",
                    "incomeVerified": true,
                    "segment": "A",
                    "asOf": "%s"
                  }
                }
                """
                        .formatted(Instant.now());

        mockMvc.perform(post("/v1/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + DecisionRoles.DECIDER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("No Active Policy"));
    }
}

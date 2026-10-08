package dev.hindsight.decision.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.testsupport.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DecisionControllerSecurityTest extends IntegrationTestBase {

    @DynamicPropertySource
    static void kafkaOff(DynamicPropertyRegistry registry) {
        registry.add("hindsight.kafka.enabled", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
        "POST,DECIDER,503",
        "POST,AUDITOR,403",
        "GET,DECIDER,403",
        "GET,AUDITOR,404"
    })
    void roleMatrix(String method, String role, int expectedStatus) throws Exception {
        if ("POST".equals(method)) {
            mockMvc.perform(post("/v1/decisions")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"requestId\":\"r1\",\"applicant\":{\"customerId\":\"c1\",\"currentLimit\":1,\"requestedIncrease\":1,\"utilization\":0.1,\"delinquencies12m\":0,\"tenureMonths\":1,\"ficoBand\":1,\"incomeBand\":\"M\",\"incomeVerified\":true,\"segment\":\"A\",\"asOf\":\"2020-01-01T00:00:00Z\"}}"))
                    .andExpect(status().is(expectedStatus));
        } else {
            mockMvc.perform(get("/v1/decisions/" + UUID.randomUUID())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().is(expectedStatus));
        }
    }

    @ParameterizedTest
    @CsvSource({"/v1/decisions,POST", "/v1/decisions/00000000-0000-0000-0000-000000000001,GET"})
    void unauthenticatedReturns401(String path, String method) throws Exception {
        if ("POST".equals(method)) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        } else {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}

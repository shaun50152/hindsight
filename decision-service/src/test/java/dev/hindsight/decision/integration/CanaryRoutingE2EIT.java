package dev.hindsight.decision.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.decision.routing.CanaryRouter;
import dev.hindsight.decision.routing.CustomerBucket;
import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.testsupport.IntegrationTestBase;
import dev.hindsight.decision.testsupport.LifecycleEventPublisher;
import dev.hindsight.decision.testsupport.TestPolicyYaml;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CanaryRoutingE2EIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    dev.hindsight.decision.cache.PolicyCache policyCache;

    @Test
    void routesCanaryCustomerToCanaryVersion() throws Exception {
        String policyId = "canary-policy";
        String yamlV1 = TestPolicyYaml.minimal(policyId, 1);
        String yamlV2 = TestPolicyYaml.canaryMarker(policyId, 2);
        int pct = 25;

        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 1, yamlV1, "ACTIVE", null, "ops");
        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 2, yamlV2, "CANARY", pct, "ops");

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> policyCache.routeFor(policyId).active().isPresent());

        String customerId = null;
        for (int i = 0; i < 500; i++) {
            String candidate = "cust-canary-" + i;
            if (CustomerBucket.bucket0to99(candidate) < pct) {
                customerId = candidate;
                break;
            }
        }
        assertThat(customerId).isNotNull();

        Policy policyV2 = PolicyYamlParser.parse(yamlV2).policy();
        String hashV2 = PolicyContentHash.hash(policyV2);

        String body =
                """
                {
                  "requestId": "req-canary-e2e",
                  "policyId": "%s",
                  "applicant": {
                    "customerId": "%s",
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
                        .formatted(policyId, customerId, Instant.now());

        MvcResult result = mockMvc.perform(post("/v1/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + DecisionRoles.DECIDER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("policyVersion").asInt()).isEqualTo(2);
        assertThat(node.get("contentHash").asString()).isEqualTo(hashV2);
        assertThat(node.get("outcome").asString()).isEqualTo("DECLINE");
    }
}

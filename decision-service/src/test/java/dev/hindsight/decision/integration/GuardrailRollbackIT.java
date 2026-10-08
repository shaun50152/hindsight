package dev.hindsight.decision.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.decision.guardrail.GuardrailScheduler;
import dev.hindsight.decision.messaging.OutboxRelay;
import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.testsupport.IntegrationTestBase;
import dev.hindsight.decision.testsupport.SidecarApplications;
import dev.hindsight.decision.testsupport.TestJwt;
import dev.hindsight.decision.testsupport.TestPolicyYaml;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import dev.hindsight.policy.lifecycle.PolicyStatus;
import dev.hindsight.policy.service.PolicyService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GuardrailRollbackIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    dev.hindsight.decision.cache.PolicyCache policyCache;

    @Autowired
    GuardrailScheduler guardrailScheduler;

    @Autowired
    OutboxRelay outboxRelay;

    @DynamicPropertySource
    static void guardrailProps(DynamicPropertyRegistry registry) {
        SidecarApplications.startPolicyAndAudit();
        registry.add(
                "hindsight.guardrail.policy-service-base-url",
                () -> "http://localhost:" + SidecarApplications.policyPort());
        registry.add("hindsight.guardrail.jwt", () -> TestJwt.tokenWithRole("OPS"));
        registry.add("hindsight.guardrail.min-sample-size", () -> "5");
        registry.add("hindsight.guardrail.evaluation-interval-ms", () -> "500");
        registry.add("hindsight.guardrail.default-thresholds.max-approval-rate-delta", () -> "0.05");
        registry.add("hindsight.guardrail.cooldown-duration", () -> "PT30S");
        registry.add("hindsight.outbox.relay-enabled", () -> "true");
    }

    @AfterAll
    static void stopSidecars() {
        SidecarApplications.stopAll();
    }

    @Test
    void guardrailTripsRollbackAndRestoresActive() throws Exception {
        String policyId = "guardrail-e2e-" + Instant.now().toEpochMilli();
        PolicyService policyService = SidecarApplications.policyService();
        String yamlV1 = TestPolicyYaml.minimal(policyId, 1);
        String yamlV2 = TestPolicyYaml.declineEveryone(policyId, 2);
        Policy policyV1 = PolicyYamlParser.parse(yamlV1).policy();
        String hashV1 = PolicyContentHash.hash(policyV1);

        policyService.createDraft(policyId, yamlV1, "maker-1");
        policyService.submit(policyId, 1, "maker-1");
        policyService.approve(policyId, 1, "checker-1");
        promoteToActive(policyService, policyId, 1, "ops-1");
        SidecarApplications.relayPolicyOutbox();

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .until(() -> hashV1.equals(policyCache.activeContentHash(policyId)));

        policyService.createDraft(policyId, yamlV2, "maker-2");
        policyService.submit(policyId, 2, "maker-2");
        policyService.approve(policyId, 2, "checker-2");
        promoteToCanary(policyService, policyId, 2, 50, "ops-1");
        SidecarApplications.relayPolicyOutbox();

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .until(() -> policyCache.routeFor(policyId).canary().isPresent());

        for (int i = 0; i < 30; i++) {
            postDecision(policyId, "cust-g-" + i, "req-g-" + i);
            outboxRelay.relay();
        }

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(300))
                .until(() -> {
                    guardrailScheduler.evaluate();
                    SidecarApplications.relayPolicyOutbox();
                    return hashV1.equals(policyCache.activeContentHash(policyId));
                });

        Integer rolledBackEvents = SidecarApplications.policyJdbc()
                .queryForObject(
                        "SELECT COUNT(*) FROM policy_events WHERE policy_id = ? AND event_type = 'ROLLED_BACK'",
                        Integer.class,
                        policyId);
        assertThat(rolledBackEvents).isEqualTo(1);

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    SidecarApplications.relayPolicyOutbox();
                    Integer lifecycleRetired = SidecarApplications.auditJdbc()
                            .queryForObject(
                                    """
                                    SELECT COUNT(*) FROM audit_log
                                    WHERE event_type = 'policy.lifecycle'
                                      AND payload->>'policyId' = ?
                                      AND payload->>'status' = 'RETIRED'
                                    """,
                                    Integer.class,
                                    policyId);
                    return lifecycleRetired != null && lifecycleRetired > 0;
                });

        JsonNode after = postDecision(policyId, "cust-after-restore", "req-after-restore");
        assertThat(after.get("contentHash").asString()).isEqualTo(hashV1);
        assertThat(after.get("outcome").asString()).isEqualTo("APPROVE");
    }

    @Test
    void noTripBelowMinSampleSize() throws Exception {
        String policyId = "guardrail-min-" + Instant.now().toEpochMilli();
        PolicyService policyService = SidecarApplications.policyService();
        policyService.createDraft(policyId, TestPolicyYaml.minimal(policyId, 1), "maker-1");
        policyService.submit(policyId, 1, "maker-1");
        policyService.approve(policyId, 1, "checker-1");
        promoteToActive(policyService, policyId, 1, "ops-1");
        policyService.createDraft(policyId, TestPolicyYaml.declineEveryone(policyId, 2), "maker-1");
        policyService.submit(policyId, 2, "maker-1");
        policyService.approve(policyId, 2, "checker-2");
        promoteToCanary(policyService, policyId, 2, 100, "ops-1");
        SidecarApplications.relayPolicyOutbox();

        postDecision(policyId, "cust-min", "req-min");
        outboxRelay.relay();
        guardrailScheduler.evaluate();

        Integer rolledBack = SidecarApplications.policyJdbc()
                .queryForObject(
                        "SELECT COUNT(*) FROM policy_events WHERE policy_id = ? AND event_type = 'ROLLED_BACK'",
                        Integer.class,
                        policyId);
        assertThat(rolledBack).isZero();
    }

    @Test
    void noSecondRollbackWithinCooldown() throws Exception {
        String policyId = "guardrail-cooldown-" + Instant.now().toEpochMilli();
        PolicyService policyService = SidecarApplications.policyService();
        policyService.createDraft(policyId, TestPolicyYaml.minimal(policyId, 1), "maker-1");
        policyService.submit(policyId, 1, "maker-1");
        policyService.approve(policyId, 1, "checker-1");
        promoteToActive(policyService, policyId, 1, "ops-1");
        policyService.createDraft(policyId, TestPolicyYaml.declineEveryone(policyId, 2), "maker-1");
        policyService.submit(policyId, 2, "maker-1");
        policyService.approve(policyId, 2, "checker-2");
        promoteToCanary(policyService, policyId, 2, 50, "ops-1");
        SidecarApplications.relayPolicyOutbox();

        for (int i = 0; i < 30; i++) {
            postDecision(policyId, "cust-cd-" + i, "req-cd-" + i);
            outboxRelay.relay();
        }
        guardrailScheduler.evaluate();
        guardrailScheduler.evaluate();
        SidecarApplications.relayPolicyOutbox();

        Integer rolledBack = SidecarApplications.policyJdbc()
                .queryForObject(
                        "SELECT COUNT(*) FROM policy_events WHERE policy_id = ? AND event_type = 'ROLLED_BACK'",
                        Integer.class,
                        policyId);
        assertThat(rolledBack).isEqualTo(1);
    }

    private static void promoteToActive(PolicyService policyService, String policyId, int version, String actor) {
        policyService.promote(policyId, version, PolicyStatus.SHADOW, null, actor);
        policyService.promote(policyId, version, PolicyStatus.CANARY, 100, actor);
        policyService.promote(policyId, version, PolicyStatus.ACTIVE, null, actor);
    }

    private static void promoteToCanary(PolicyService policyService, String policyId, int version, int pct, String actor) {
        policyService.promote(policyId, version, PolicyStatus.SHADOW, null, actor);
        policyService.promote(policyId, version, PolicyStatus.CANARY, pct, actor);
    }

    private JsonNode postDecision(String policyId, String customerId, String requestId) throws Exception {
        String body =
                """
                {
                  "requestId": "%s",
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
                        .formatted(requestId, policyId, customerId, Instant.now());
        var result = mockMvc.perform(post("/v1/decisions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + DecisionRoles.DECIDER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}

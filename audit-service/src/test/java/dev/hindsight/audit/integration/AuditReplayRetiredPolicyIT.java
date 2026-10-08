package dev.hindsight.audit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hindsight.audit.persistence.AuditLogRepository;
import dev.hindsight.audit.security.AuditRoles;
import dev.hindsight.audit.testsupport.IntegrationTestBase;
import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.policyengine.evaluate.PolicyEvaluator;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.ApplicantSnapshot;
import dev.hindsight.policyengine.model.Decision;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.compile.PolicyCompiler;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuditReplayRetiredPolicyIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID DECISION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void replaysDecisionMadeUnderRetiredPolicyVersion() throws Exception {
        jdbcTemplate.execute("TRUNCATE audit_log, audit_checkpoints RESTART IDENTITY");
        String yaml =
                """
                policyId: retired-p
                version: 3
                description: t
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "applicant.ficoBand >= 2"
                    outcome: APPROVE
                    reason: RC_STRONG
                """;
        Policy policy = PolicyYamlParser.parse(yaml).policy();
        String hash = PolicyContentHash.hash(policy);
        var compiled = PolicyCompiler.compileValidatedPolicy(policy).compiled().orElseThrow();
        ApplicantSnapshot applicant = new ApplicantSnapshot(
                "c1", 5000, 100, 0.2, 0, 24, 4, "M", true, "A", Instant.parse("2020-01-01T00:00:00Z"));
        Decision decision = PolicyEvaluator.evaluate(compiled, applicant);

        PolicyLifecycleEvent lifecycle = new PolicyLifecycleEvent(
                "retired-p", 3, hash, "RETIRED", null, "ops", Instant.now(), 1, yaml);
        kafkaTemplate
                .send("policy.lifecycle", PolicyLifecycleEvent.messageKey("retired-p", 3), JSON.writeValueAsString(lifecycle))
                .get();

        DecisionMadePayload payload = new DecisionMadePayload(
                DECISION_ID.toString(),
                "req-retired",
                "retired-p",
                3,
                hash,
                decision.outcome().name(),
                decision.reasonCodes().stream().map(Enum::name).toList(),
                decision.maxIncrease().orElse(null),
                List.of(),
                new DecisionMadePayload.ApplicantSnapshotPayload(
                        applicant.customerId(),
                        applicant.currentLimit(),
                        applicant.requestedIncrease(),
                        applicant.utilization(),
                        applicant.delinquencies12m(),
                        applicant.tenureMonths(),
                        applicant.ficoBand(),
                        applicant.incomeBand(),
                        applicant.incomeVerified(),
                        applicant.segment(),
                        applicant.asOf()),
                null,
                null,
                null);
        kafkaTemplate
                .send(
                        "decision.made",
                        "c1",
                        JSON.writeValueAsString(new EventEnvelope(
                                "retired-evt",
                                "decision.made",
                                applicant.asOf(),
                                "req-retired",
                                JSON.valueToTree(payload))))
                .get();

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> auditLogRepository.countByChain("main") >= 2);

        var result = mockMvc.perform(post("/v1/audit/replay/" + DECISION_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + AuditRoles.AUDITOR))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("MATCH");
    }
}

package dev.hindsight.audit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.audit.persistence.AuditLogRepository;
import dev.hindsight.audit.testsupport.IntegrationTestBase;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class AuditIngestionIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void ingestsDecisionAndLifecycleWithGaplessSeq() throws Exception {
        jdbcTemplate.execute("TRUNCATE audit_log, audit_checkpoints RESTART IDENTITY");
        String yaml =
                """
                policyId: p1
                version: 1
                description: t
                inputs: applicant
                defaultOutcome: REFER
                rules:
                  - id: R1
                    when: "applicant.ficoBand >= 1"
                    outcome: APPROVE
                    reason: RC_STRONG
                """;
        Policy policy = PolicyYamlParser.parse(yaml).policy();
        PolicyLifecycleEvent lifecycle = new PolicyLifecycleEvent(
                "p1",
                1,
                PolicyContentHash.hash(policy),
                "ACTIVE",
                null,
                "ops",
                Instant.now(),
                1,
                yaml);
        kafkaTemplate
                .send("policy.lifecycle", PolicyLifecycleEvent.messageKey("p1", 1), JSON.writeValueAsString(lifecycle))
                .get();

        EventEnvelope decision = new EventEnvelope(
                "evt-decision-1",
                "decision.made",
                Instant.now(),
                "corr-1",
                JSON.readTree(
                        """
                        {"decisionId":"%s","requestId":"r1","policyId":"p1","policyVersion":1,"contentHash":"x","outcome":"APPROVE","reasonCodes":["RC_STRONG"],"maxIncrease":null,"rulesEvaluated":[],"applicant":{"customerId":"c1","currentLimit":1,"requestedIncrease":1,"utilization":0.1,"delinquencies12m":0,"tenureMonths":1,"ficoBand":1,"incomeBand":"M","incomeVerified":true,"segment":"A","asOf":"2020-01-01T00:00:00Z"}}
                        """
                                .formatted("11111111-1111-1111-1111-111111111111")));
        kafkaTemplate.send("decision.made", "c1", JSON.writeValueAsString(decision)).get();

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> auditLogRepository.countByChain("main") >= 2);

        assertThat(auditLogRepository.findByChainAndSeq("main", 1)).isPresent();
        assertThat(auditLogRepository.findByChainAndSeq("main", 2)).isPresent();
    }
}

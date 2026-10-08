package dev.hindsight.decision.testsupport;

import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.decision.messaging.DecisionTopics;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Instant;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

public final class LifecycleEventPublisher {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private LifecycleEventPublisher() {}

    public static void publish(
            KafkaTemplate<String, String> kafkaTemplate,
            String policyId,
            int version,
            String yaml,
            String status,
            Integer canaryPct,
            String actor)
            throws Exception {
        Policy policy = PolicyYamlParser.parse(yaml).policy();
        String hash = PolicyContentHash.hash(policy);
        PolicyLifecycleEvent event = new PolicyLifecycleEvent(
                policyId,
                version,
                hash,
                status,
                canaryPct,
                actor,
                null,
                Instant.now(),
                PolicyLifecycleEvent.CURRENT_SCHEMA_VERSION,
                yaml);
        kafkaTemplate
                .send(
                        DecisionTopics.POLICY_LIFECYCLE,
                        PolicyLifecycleEvent.messageKey(policyId, version),
                        JSON.writeValueAsString(event))
                .get();
    }
}

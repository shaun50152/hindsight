package dev.hindsight.decision.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.decision.testsupport.IntegrationTestBase;
import dev.hindsight.decision.testsupport.LifecycleEventPublisher;
import dev.hindsight.decision.testsupport.TestPolicyYaml;
import dev.hindsight.policyengine.hash.PolicyContentHash;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class RollbackRoutingIT extends IntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    dev.hindsight.decision.cache.PolicyCache policyCache;

    @Test
    void rollbackRestoresPreviousActiveWithinBoundedTime() throws Exception {
        String policyId = "rollback-policy";
        String yamlV1 = TestPolicyYaml.minimal(policyId, 1);
        String yamlV2 = TestPolicyYaml.minimal(policyId, 2);
        String hashV1 = PolicyContentHash.hash(PolicyYamlParser.parse(yamlV1).policy());

        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 1, yamlV1, "ACTIVE", null, "ops");
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> hashV1.equals(policyCache.activeContentHash(policyId)));

        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 1, yamlV1, "RETIRED", null, "ops");
        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 2, yamlV2, "ACTIVE", null, "ops");
        Policy policyV2 = PolicyYamlParser.parse(yamlV2).policy();
        String hashV2 = PolicyContentHash.hash(policyV2);
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> hashV2.equals(policyCache.activeContentHash(policyId)));

        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 2, yamlV2, "RETIRED", null, "ops");
        LifecycleEventPublisher.publish(kafkaTemplate, policyId, 1, yamlV1, "ACTIVE", null, "system");

        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> hashV1.equals(policyCache.activeContentHash(policyId)));

        assertThat(policyCache.activeContentHash(policyId)).isEqualTo(hashV1);
    }
}

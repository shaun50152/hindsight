package dev.hindsight.decision.cache;

import dev.hindsight.common.events.PolicyLifecycleEvent;
import dev.hindsight.decision.messaging.DecisionTopics;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "hindsight.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PolicyCacheBootstrap {

    private static final Logger log = LoggerFactory.getLogger(PolicyCacheBootstrap.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PolicyCache policyCache;
    private final ConsumerFactory<String, String> consumerFactory;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final long bootstrapTimeoutMs;

    public PolicyCacheBootstrap(
            PolicyCache policyCache,
            ConsumerFactory<String, String> consumerFactory,
            @Value("${hindsight.policy-cache.bootstrap-timeout-ms:30000}") long bootstrapTimeoutMs) {
        this.policyCache = policyCache;
        this.consumerFactory = consumerFactory;
        this.bootstrapTimeoutMs = bootstrapTimeoutMs;
    }

    public boolean isReady() {
        return ready.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    void replayPolicyLifecycleTopic() {
        var props = new HashMap<>(consumerFactory.getConfigurationProperties());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "decision-policy-cache-bootstrap-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(DecisionTopics.POLICY_LIFECYCLE));
            consumer.poll(Duration.ofMillis(500));
            var end = System.currentTimeMillis() + bootstrapTimeoutMs;
            var assignment = consumer.assignment();
            for (var partition : assignment) {
                consumer.seekToBeginning(Collections.singletonList(partition));
            }
            while (System.currentTimeMillis() < end) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    break;
                }
                records.forEach(record -> applyRecord(record.value()));
            }
        } catch (Exception e) {
            log.warn("Policy cache bootstrap from Kafka failed: {}", e.getMessage());
        }
        ready.set(true);
        log.info("Policy cache bootstrap finished");
    }

    void applyRecord(String json) {
        try {
            PolicyLifecycleEvent event = JSON.readValue(json, PolicyLifecycleEvent.class);
            policyCache.apply(event);
        } catch (Exception e) {
            log.warn("Failed to apply lifecycle record during bootstrap: {}", e.getMessage());
        }
    }
}

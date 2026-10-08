package dev.hindsight.policy.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "hindsight.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    NewTopic policyLifecycleTopic() {
        return TopicBuilder.name(OutboxWriter.POLICY_LIFECYCLE_TOPIC)
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }
}

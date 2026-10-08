package dev.hindsight.audit.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic auditCheckpointTopic() {
        return TopicBuilder.name(AuditTopics.AUDIT_CHECKPOINT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

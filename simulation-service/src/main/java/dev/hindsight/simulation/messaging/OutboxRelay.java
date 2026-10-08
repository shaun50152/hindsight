package dev.hindsight.simulation.messaging;

import dev.hindsight.simulation.persistence.OutboxMessage;
import dev.hindsight.simulation.persistence.OutboxRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "hindsight.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public OutboxRelay(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            TransactionTemplate transactionTemplate,
            @Value("${hindsight.outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${hindsight.outbox.relay-interval-ms:1000}")
    @ConditionalOnProperty(name = "hindsight.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
    public void relay() {
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxMessage> batch = outboxRepository.claimBatch(batchSize);
            for (OutboxMessage message : batch) {
                try {
                    kafkaTemplate.send(message.topic(), message.messageKey(), message.payloadJson()).get();
                    outboxRepository.markPublished(message.id());
                } catch (Exception e) {
                    outboxRepository.incrementAttempts(message.id());
                }
            }
        });
    }
}

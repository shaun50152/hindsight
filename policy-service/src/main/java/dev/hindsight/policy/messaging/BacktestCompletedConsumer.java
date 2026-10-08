package dev.hindsight.policy.messaging;

import dev.hindsight.common.events.BacktestCompletedPayload;
import dev.hindsight.common.events.EventEnvelope;
import dev.hindsight.common.events.SimulationTopics;
import dev.hindsight.policy.persistence.BacktestReportRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "hindsight.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class BacktestCompletedConsumer {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BacktestReportRepository backtestReportRepository;

    public BacktestCompletedConsumer(BacktestReportRepository backtestReportRepository) {
        this.backtestReportRepository = backtestReportRepository;
    }

    @KafkaListener(topics = SimulationTopics.BACKTEST_COMPLETED, groupId = "${spring.kafka.consumer.group-id}-backtest")
    void onBacktestCompleted(String raw) throws Exception {
        EventEnvelope envelope = JSON.readValue(raw, EventEnvelope.class);
        BacktestCompletedPayload payload = JSON.treeToValue(envelope.payload(), BacktestCompletedPayload.class);
        backtestReportRepository.upsert(
                payload.candidateContentHash(),
                payload.backtestId(),
                payload.status().name(),
                JSON.writeValueAsString(payload.summary()));
    }
}

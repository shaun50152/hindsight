package dev.hindsight.backtest.kafka;

import dev.hindsight.backtest.source.DecisionRecord;
import dev.hindsight.backtest.source.DecisionSource;
import dev.hindsight.common.events.DecisionMadePayload;
import dev.hindsight.common.events.EventEnvelope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import tools.jackson.databind.json.JsonMapper;

public final class KafkaDecisionSource implements DecisionSource {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<DecisionRecord> loaded;

    public KafkaDecisionSource(
            String bootstrapServers, String topic, String groupId, List<ShardAssignment> assignments) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none");
        List<DecisionRecord> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<ShardAssignment> sorted = assignments.stream()
                    .sorted(Comparator.comparingInt(ShardAssignment::partition)
                            .thenComparingLong(ShardAssignment::startOffset))
                    .toList();
            for (ShardAssignment assignment : sorted) {
                TopicPartition tp = new TopicPartition(topic, assignment.partition());
                consumer.assign(List.of(tp));
                consumer.seek(tp, assignment.startOffset());
                long endExclusive = assignment.endOffsetExclusive();
                while (consumer.position(tp) < endExclusive) {
                    var polled = consumer.poll(Duration.ofMillis(500));
                    if (polled.isEmpty()) {
                        break;
                    }
                    for (ConsumerRecord<String, String> record : polled) {
                        if (record.partition() != assignment.partition()) {
                            continue;
                        }
                        if (record.offset() >= endExclusive) {
                            break;
                        }
                        DecisionRecord decision = parse(record.value());
                        if (decision != null) {
                            records.add(decision);
                        }
                    }
                }
            }
        }
        this.loaded = List.copyOf(records);
    }

    private static DecisionRecord parse(String raw) {
        try {
            EventEnvelope envelope = JSON.readValue(raw, EventEnvelope.class);
            DecisionMadePayload payload = JSON.treeToValue(envelope.payload(), DecisionMadePayload.class);
            return new DecisionRecord(payload);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public java.util.stream.Stream<DecisionRecord> records() {
        return loaded.stream();
    }
}

package dev.hindsight.simulation.backtest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

public final class KafkaOffsetCapture {

    private KafkaOffsetCapture() {}

    public static Map<Integer, ShardPlanner.PartitionRange> captureEndOffsets(
            String bootstrapServers, String topic, List<Integer> partitions, Map<Integer, Long> startOffsets) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "backtest-offset-capture");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        Map<Integer, ShardPlanner.PartitionRange> ranges = new HashMap<>();
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> tps =
                    partitions.stream().map(p -> new TopicPartition(topic, p)).toList();
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(tps);
            for (TopicPartition tp : tps) {
                long start = startOffsets.getOrDefault(tp.partition(), 0L);
                long end = endOffsets.getOrDefault(tp, start);
                ranges.put(tp.partition(), new ShardPlanner.PartitionRange(start, end));
            }
        }
        return ranges;
    }
}

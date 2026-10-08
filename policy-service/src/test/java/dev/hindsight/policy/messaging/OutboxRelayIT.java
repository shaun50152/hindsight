package dev.hindsight.policy.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.policy.testsupport.PostgresTestSupport;
import dev.hindsight.policy.testsupport.TestPolicyYaml;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class OutboxRelayIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerPolicySchema(postgres, registry);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("hindsight.jwt.hmac-secret", () -> "test-jwt-hmac-secret-32bytes-min!!");
        registry.add("hindsight.kafka.enabled", () -> "true");
        registry.add("hindsight.outbox.relay-enabled", () -> "true");
    }

    @Autowired
    dev.hindsight.policy.service.PolicyService policyService;

    @Autowired
    OutboxRelay outboxRelay;

    @Test
    void lifecycleChangeIsRelayedToKafka() throws Exception {
        String policyId = "outbox-relay-policy";
        policyService.createDraft(policyId, TestPolicyYaml.minimal(policyId, 1), "maker-1");

        var consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(), "test-group", "true");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var consumer = new DefaultKafkaConsumerFactory<>(
                        consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(OutboxWriter.POLICY_LIFECYCLE_TOPIC));

        ConsumerRecord<String, String> match = null;
        for (int attempt = 0; attempt < 40 && match == null; attempt++) {
            outboxRelay.relay();
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
            for (var record : records) {
                if (policyId.equals(record.key())) {
                    match = record;
                    break;
                }
            }
            Thread.sleep(250);
        }
        consumer.close();

        assertThat(match).isNotNull();
        assertThat(match.value()).contains(policyId);
    }
}

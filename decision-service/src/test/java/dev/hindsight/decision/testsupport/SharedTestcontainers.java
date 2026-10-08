package dev.hindsight.decision.testsupport;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One Postgres + Kafka pair for all decision-service integration tests (avoids port races). */
public final class SharedTestcontainers {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");
    public static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    private SharedTestcontainers() {}
}

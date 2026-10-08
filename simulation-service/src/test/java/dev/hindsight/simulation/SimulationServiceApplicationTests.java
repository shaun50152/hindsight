package dev.hindsight.simulation;

import dev.hindsight.simulation.testsupport.SimulationPostgresTestSupport;
import dev.hindsight.simulation.testsupport.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class SimulationServiceApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        SimulationPostgresTestSupport.registerSchemas(postgres, registry);
        registry.add("hindsight.jwt.hmac-secret", () -> TestJwt.SECRET);
        registry.add("hindsight.kafka.enabled", () -> "false");
        registry.add("hindsight.outbox.relay-enabled", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("spring.kafka.consumer.group-id", () -> "simulation-context-test");
        registry.add("hindsight.backtest.runner", () -> "local");
    }

    @Test
    void contextLoads() {}
}

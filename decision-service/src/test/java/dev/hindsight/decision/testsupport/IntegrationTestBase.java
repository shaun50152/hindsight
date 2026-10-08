package dev.hindsight.decision.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void baseProps(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerDecisionSchema(SharedTestcontainers.POSTGRES, registry);
        registry.add("spring.kafka.bootstrap-servers", SharedTestcontainers.KAFKA::getBootstrapServers);
        registry.add("hindsight.jwt.hmac-secret", () -> "test-jwt-hmac-secret-32bytes-min!!");
        registry.add("hindsight.kafka.enabled", () -> "true");
        registry.add("hindsight.outbox.relay-enabled", () -> "false");
    }

    protected static String kafkaBootstrap() {
        return SharedTestcontainers.KAFKA.getBootstrapServers();
    }
}

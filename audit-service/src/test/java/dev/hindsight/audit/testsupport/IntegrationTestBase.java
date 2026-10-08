package dev.hindsight.audit.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void baseProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> SharedTestcontainers.POSTGRES.getJdbcUrl()
                        + (SharedTestcontainers.POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                        + "currentSchema=audit");
        registry.add("spring.datasource.username", SharedTestcontainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", SharedTestcontainers.POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", SharedTestcontainers.KAFKA::getBootstrapServers);
        registry.add("hindsight.jwt.hmac-secret", () -> "test-jwt-hmac-secret-32bytes-min!!");
    }
}

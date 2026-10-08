package dev.hindsight.policy.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestSupport {

    private PostgresTestSupport() {}

    public static void registerPolicySchema(PostgreSQLContainer postgres, DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=policy");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:policy/flyway");
        registry.add("spring.flyway.repair-on-migrate", () -> "true");
    }
}

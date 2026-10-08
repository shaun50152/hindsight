package dev.hindsight.simulation.testsupport;

import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class SimulationPostgresTestSupport {

    private SimulationPostgresTestSupport() {}

    public static void registerSchemas(PostgreSQLContainer postgres, DynamicPropertyRegistry registry) {
        String base = postgres.getJdbcUrl();
        registry.add("spring.datasource.url", () -> base + (base.contains("?") ? "&" : "?") + "currentSchema=simulation");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:simulation/flyway");
        registry.add("spring.flyway.schemas", () -> "simulation");
        registry.add("spring.flyway.default-schema", () -> "simulation");
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add(
                "hindsight.backtest.policy-datasource.url",
                () -> base + (base.contains("?") ? "&" : "?") + "currentSchema=policy");
        registry.add("hindsight.backtest.policy-datasource.username", postgres::getUsername);
        registry.add("hindsight.backtest.policy-datasource.password", postgres::getPassword);
        migratePolicySchema(postgres);
    }

    private static void migratePolicySchema(PostgreSQLContainer postgres) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("policy")
                .defaultSchema("policy")
                .createSchemas(true)
                .locations("classpath:policy/flyway")
                .load()
                .migrate();
    }
}

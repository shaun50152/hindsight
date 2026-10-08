package dev.hindsight.decision.testsupport;

import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class MultiSchemaPostgres {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private MultiSchemaPostgres() {}

    /** One shared Testcontainers DB: recreate service schemas once per JVM (Flyway paths are per-service). */
    public static void ensureSchemas() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(SharedTestcontainers.POSTGRES.getJdbcUrl());
        ds.setUsername(SharedTestcontainers.POSTGRES.getUsername());
        ds.setPassword(SharedTestcontainers.POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA IF EXISTS decision CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS policy CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS audit CASCADE");
        jdbc.execute("CREATE SCHEMA decision");
        jdbc.execute("CREATE SCHEMA policy");
        jdbc.execute("CREATE SCHEMA audit");
        migrateSchema("decision", "classpath:decision/flyway");
        migrateSchema("policy", "classpath:policy/flyway");
        migrateSchema("audit", "classpath:audit/flyway");
    }

    private static void migrateSchema(String schema, String locations) {
        Flyway.configure()
                .dataSource(
                        jdbcUrlForSchema(schema),
                        SharedTestcontainers.POSTGRES.getUsername(),
                        SharedTestcontainers.POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(false)
                .locations(locations)
                .load()
                .migrate();
    }

    public static String jdbcUrlForSchema(String schema) {
        String base = SharedTestcontainers.POSTGRES.getJdbcUrl();
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "currentSchema=" + schema;
    }
}

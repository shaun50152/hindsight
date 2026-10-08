package dev.hindsight.decision.testsupport;

import dev.hindsight.decision.DecisionServiceApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public final class ShadowSidecar {

    private ShadowSidecar() {}

    public static ConfigurableApplicationContext start(boolean simulateCrash) {
        MultiSchemaPostgres.ensureSchemas();
        Map<String, String> map = new LinkedHashMap<>();
        map.put("spring.main.web-application-type", "none");
        map.put("spring.datasource.url", MultiSchemaPostgres.jdbcUrlForSchema("decision"));
        map.put("spring.datasource.username", SharedTestcontainers.POSTGRES.getUsername());
        map.put("spring.datasource.password", SharedTestcontainers.POSTGRES.getPassword());
        map.put("spring.kafka.bootstrap-servers", SharedTestcontainers.KAFKA.getBootstrapServers());
        map.put("spring.kafka.consumer.group-id", "decision-service-shadow-isolation");
        map.put("spring.flyway.locations", "classpath:decision/flyway");
        map.put("spring.flyway.repair-on-migrate", "true");
        map.put("hindsight.jwt.hmac-secret", "test-jwt-hmac-secret-32bytes-min!!");
        map.put("hindsight.kafka.enabled", "true");
        map.put("hindsight.outbox.relay-enabled", "true");
        map.put("hindsight.guardrail.enabled", "false");
        map.put("hindsight.shadow.simulate-crash", Boolean.toString(simulateCrash));
        String[] inlined = map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(DecisionServiceApplication.class)
                .profiles("shadow")
                .initializers(ctx -> TestPropertySourceUtils.addInlinedPropertiesToEnvironment(ctx, inlined))
                .run();
    }
}

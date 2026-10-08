package dev.hindsight.decision.testsupport;

import dev.hindsight.audit.AuditServiceApplication;
import dev.hindsight.policy.PolicyServiceApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

public final class SidecarApplications {

    private static ConfigurableApplicationContext policyContext;
    private static ConfigurableApplicationContext auditContext;

    private SidecarApplications() {}

    public static synchronized void startPolicyAndAudit() {
        if (policyContext != null) {
            return;
        }
        MultiSchemaPostgres.ensureSchemas();
        String kafka = SharedTestcontainers.KAFKA.getBootstrapServers();
        String jwt = "test-jwt-hmac-secret-32bytes-min!!";
        Map<String, String> policyProps = new LinkedHashMap<>();
        policyProps.put("server.port", "0");
        policyProps.put("spring.flyway.enabled", "false");
        policyProps.put("spring.datasource.url", MultiSchemaPostgres.jdbcUrlForSchema("policy"));
        policyProps.put("spring.datasource.username", SharedTestcontainers.POSTGRES.getUsername());
        policyProps.put("spring.datasource.password", SharedTestcontainers.POSTGRES.getPassword());
        policyProps.put("spring.kafka.bootstrap-servers", kafka);
        policyProps.put("hindsight.jwt.hmac-secret", jwt);
        policyProps.put("hindsight.kafka.enabled", "true");
        policyProps.put("hindsight.outbox.relay-enabled", "true");
        policyProps.put("hindsight.outbox.relay-interval-ms", "200");
        policyContext = new SpringApplicationBuilder(PolicyServiceApplication.class)
                .initializers(ctx -> TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                        ctx, inlinedProperties(policyProps)))
                .run();

        Map<String, String> auditProps = new LinkedHashMap<>();
        auditProps.put("server.port", "0");
        auditProps.put("spring.flyway.enabled", "false");
        auditProps.put("spring.datasource.url", MultiSchemaPostgres.jdbcUrlForSchema("audit"));
        auditProps.put("spring.datasource.username", SharedTestcontainers.POSTGRES.getUsername());
        auditProps.put("spring.datasource.password", SharedTestcontainers.POSTGRES.getPassword());
        auditProps.put("spring.kafka.bootstrap-servers", kafka);
        auditProps.put("hindsight.jwt.hmac-secret", jwt);
        auditProps.put("hindsight.kafka.enabled", "true");
        auditProps.put("spring.kafka.consumer.auto-offset-reset", "earliest");
        auditProps.put("spring.kafka.consumer.group-id", "audit-service-guardrail-sidecar");
        auditContext = new SpringApplicationBuilder(AuditServiceApplication.class)
                .initializers(ctx -> TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                        ctx, inlinedProperties(auditProps)))
                .run();
    }

    public static int policyPort() {
        return policyContext.getEnvironment().getProperty("local.server.port", Integer.class);
    }

    public static dev.hindsight.policy.service.PolicyService policyService() {
        return policyContext.getBean(dev.hindsight.policy.service.PolicyService.class);
    }

    public static org.springframework.jdbc.core.JdbcTemplate auditJdbc() {
        return auditContext.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
    }

    public static org.springframework.jdbc.core.JdbcTemplate policyJdbc() {
        return policyContext.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
    }

    public static void relayPolicyOutbox() {
        policyContext.getBean(dev.hindsight.policy.messaging.OutboxRelay.class).relay();
    }

    public static synchronized void stopAll() {
        if (auditContext != null) {
            auditContext.close();
            auditContext = null;
        }
        if (policyContext != null) {
            policyContext.close();
            policyContext = null;
        }
    }

    static String[] toProperties(Map<String, String> map) {
        return map.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new);
    }

    static Map<String, Object> toPropertyMap(Map<String, String> map) {
        return Map.copyOf(map);
    }

    private static String[] inlinedProperties(Map<String, String> map) {
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
    }
}

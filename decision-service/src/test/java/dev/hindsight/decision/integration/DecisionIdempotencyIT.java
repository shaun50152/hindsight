package dev.hindsight.decision.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.testsupport.IntegrationTestBase;
import dev.hindsight.decision.testsupport.LifecycleEventPublisher;
import dev.hindsight.decision.testsupport.TestJwt;
import dev.hindsight.decision.testsupport.TestPolicyYaml;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DecisionIdempotencyIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    dev.hindsight.decision.cache.PolicyCache policyCache;

    @BeforeEach
    void publishActivePolicy() throws Exception {
        String policyId = "credit-line-increase";
        LifecycleEventPublisher.publish(
                kafkaTemplate, policyId, 1, TestPolicyYaml.minimal(policyId, 1), "ACTIVE", null, "ops");
        org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> policyCache.activeContentHash(policyId) != null);
    }

    @Test
    void concurrentIdenticalRequestsProduceOneDecision() throws Exception {
        String body =
                """
                {
                  "requestId": "req-idem-50",
                  "applicant": {
                    "customerId": "cust-idem",
                    "currentLimit": 5000,
                    "requestedIncrease": 500,
                    "utilization": 0.3,
                    "delinquencies12m": 0,
                    "tenureMonths": 24,
                    "ficoBand": 4,
                    "incomeBand": "M",
                    "incomeVerified": true,
                    "segment": "A",
                    "asOf": "%s"
                  }
                }
                """
                        .formatted(Instant.now());

        HttpClient client = HttpClient.newHttpClient();
        String token = TestJwt.tokenWithRole(DecisionRoles.DECIDER);
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/v1/decisions"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                JsonNode node = JSON.readTree(response.body());
                return node.get("decisionId").asString();
            });
        }
        List<Future<String>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        List<String> ids = new ArrayList<>();
        for (Future<String> f : futures) {
            ids.add(f.get());
        }
        assertThat(ids).hasSize(50).doesNotContainNull();
        assertThat(ids.stream().distinct()).hasSize(1);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM decisions WHERE request_id = ?", Long.class, "req-idem-50");
        assertThat(count).isEqualTo(1);
    }
}

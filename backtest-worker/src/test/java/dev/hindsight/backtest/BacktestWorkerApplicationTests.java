package dev.hindsight.backtest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        properties = {
            "hindsight.backtest.worker-enabled=false",
            "hindsight.backtest.backtest-id=",
            "spring.flyway.enabled=false"
        })
@Testcontainers
class BacktestWorkerApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("hindsight.backtest.policy-datasource.url", postgres::getJdbcUrl);
        registry.add("hindsight.backtest.policy-datasource.username", postgres::getUsername);
        registry.add("hindsight.backtest.policy-datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {}
}

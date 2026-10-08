package dev.hindsight.decision;

import dev.hindsight.decision.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class DecisionServiceApplicationTests extends IntegrationTestBase {

    @DynamicPropertySource
    static void kafkaOff(DynamicPropertyRegistry registry) {
        registry.add("hindsight.kafka.enabled", () -> "false");
    }

    @Test
    void contextLoads() {}
}

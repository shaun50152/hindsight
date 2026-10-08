package dev.hindsight.decision.shadow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.decision.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("shadow")
@Testcontainers
class ShadowProfileContextTest extends IntegrationTestBase {

    @Autowired
    ApplicationContext context;

    @Test
    void loadsShadowConsumerWithoutWebControllers() {
        assertThat(context.getBeansOfType(ShadowDecisionMadeConsumer.class)).isNotEmpty();
        assertThat(context.containsBean("decisionController")).isFalse();
    }
}

package dev.hindsight.decision.guardrail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!shadow")
@EnableConfigurationProperties(GuardrailProperties.class)
public class GuardrailConfig {}

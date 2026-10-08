package dev.hindsight.decision.guardrail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("!shadow")
@ConditionalOnProperty(name = "hindsight.guardrail.enabled", havingValue = "true", matchIfMissing = true)
public class GuardrailRollbackClient {

    private final RestClient restClient;
    private final GuardrailProperties properties;

    public GuardrailRollbackClient(GuardrailProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.getPolicyServiceBaseUrl()).build();
    }

    public void rollback(String policyId, int version, String reason) {
        String jwt = properties.getJwt();
        if (jwt == null || jwt.isBlank()) {
            throw new IllegalStateException("hindsight.guardrail.jwt must be set for guardrail rollback");
        }
        restClient
                .post()
                .uri("/v1/policies/{policyId}/versions/{version}/rollback", policyId, version)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .body(new RollbackBody(reason))
                .retrieve()
                .toBodilessEntity();
    }

    private record RollbackBody(String reason) {}
}

package dev.hindsight.decision.api;

import dev.hindsight.decision.api.dto.CreateDecisionRequest;
import dev.hindsight.decision.api.dto.DecisionResponse;
import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.service.DecisionApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/decisions")
@Profile("!shadow")
public class DecisionController {

    private final DecisionApplicationService decisionService;

    public DecisionController(DecisionApplicationService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('" + DecisionRoles.DECIDER + "')")
    DecisionResponse decide(@Valid @RequestBody CreateDecisionRequest request, HttpServletRequest httpRequest) {
        long latencyMs = DecisionLatencyFilter.latencyMs(httpRequest);
        return decisionService.decide(request, latencyMs);
    }

    @GetMapping("/{decisionId}")
    @PreAuthorize("hasRole('" + DecisionRoles.AUDITOR + "')")
    DecisionResponse get(@PathVariable UUID decisionId) {
        return decisionService.get(decisionId);
    }
}

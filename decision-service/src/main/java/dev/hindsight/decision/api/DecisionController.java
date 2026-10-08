package dev.hindsight.decision.api;

import dev.hindsight.decision.api.dto.CreateDecisionRequest;
import dev.hindsight.decision.api.dto.DecisionResponse;
import dev.hindsight.decision.security.DecisionRoles;
import dev.hindsight.decision.service.DecisionApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/decisions")
public class DecisionController {

    private final DecisionApplicationService decisionService;

    public DecisionController(DecisionApplicationService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('" + DecisionRoles.DECIDER + "')")
    DecisionResponse decide(@Valid @RequestBody CreateDecisionRequest request) {
        return decisionService.decide(request);
    }

    @GetMapping("/{decisionId}")
    @PreAuthorize("hasRole('" + DecisionRoles.AUDITOR + "')")
    DecisionResponse get(@PathVariable UUID decisionId) {
        return decisionService.get(decisionId);
    }
}

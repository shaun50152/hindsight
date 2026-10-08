package dev.hindsight.decision.service;

import dev.hindsight.decision.api.dto.CreateDecisionRequest;
import dev.hindsight.decision.api.dto.DecisionResponse;
import dev.hindsight.decision.persistence.DecisionRecord;
import dev.hindsight.decision.persistence.DecisionRepository;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Profile("!shadow")
public class DecisionApplicationService {

    private final DecisionRepository decisionRepository;
    private final DecisionWriteService decisionWriteService;

    public DecisionApplicationService(
            DecisionRepository decisionRepository, DecisionWriteService decisionWriteService) {
        this.decisionRepository = decisionRepository;
        this.decisionWriteService = decisionWriteService;
    }

    public DecisionResponse decide(CreateDecisionRequest request, long latencyMs) {
        return decisionRepository
                .findByRequestId(request.requestId())
                .map(DecisionWriteService::toResponse)
                .orElseGet(() -> createOrLoadExisting(request, latencyMs));
    }

    public DecisionResponse get(UUID decisionId) {
        DecisionRecord record = decisionRepository
                .findByDecisionId(decisionId)
                .orElseThrow(() -> new DecisionNotFoundException(decisionId));
        return DecisionWriteService.toResponse(record);
    }

    private DecisionResponse createOrLoadExisting(CreateDecisionRequest request, long latencyMs) {
        try {
            return decisionWriteService.create(request, latencyMs);
        } catch (DataIntegrityViolationException e) {
            return decisionRepository
                    .findByRequestId(request.requestId())
                    .map(DecisionWriteService::toResponse)
                    .orElseThrow(() -> e);
        }
    }
}

package dev.hindsight.audit.api;

import dev.hindsight.audit.chain.AuditVerifyService;
import dev.hindsight.audit.replay.AuditReplayService;
import dev.hindsight.audit.security.AuditRoles;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private final AuditVerifyService verifyService;
    private final AuditReplayService replayService;

    public AuditController(AuditVerifyService verifyService, AuditReplayService replayService) {
        this.verifyService = verifyService;
        this.replayService = replayService;
    }

    @GetMapping("/verify")
    @PreAuthorize("hasRole('" + AuditRoles.AUDITOR + "')")
    AuditVerifyService.VerifyResult verify(
            @RequestParam long from, @RequestParam(name = "to") long to) {
        return verifyService.verify(from, to);
    }

    @PostMapping("/replay/{decisionId}")
    @PreAuthorize("hasRole('" + AuditRoles.AUDITOR + "')")
    AuditReplayService.ReplayResult replay(
            @PathVariable UUID decisionId, @RequestParam Optional<Integer> policyVersion) {
        return replayService.replay(decisionId, policyVersion);
    }
}

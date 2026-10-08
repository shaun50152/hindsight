package dev.hindsight.policy.api;

import dev.hindsight.policy.api.dto.CreateDraftRequest;
import dev.hindsight.policy.api.dto.PolicyVersionResponse;
import dev.hindsight.policy.api.dto.PolicyVersionSummaryResponse;
import dev.hindsight.policy.api.dto.PromoteRequest;
import dev.hindsight.policy.api.dto.RollbackRequest;
import dev.hindsight.policy.api.dto.RuleDiffResponse;
import dev.hindsight.policy.persistence.PolicyRecord;
import dev.hindsight.policy.security.PolicyRoles;
import dev.hindsight.policy.service.PolicyRuleDiffService;
import dev.hindsight.policy.service.PolicyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/policies")
public class PolicyController {

    private final PolicyService policyService;
    private final PolicyRuleDiffService diffService;
    private final PolicyResponseMapper mapper;

    public PolicyController(
            PolicyService policyService, PolicyRuleDiffService diffService, PolicyResponseMapper mapper) {
        this.policyService = policyService;
        this.diffService = diffService;
        this.mapper = mapper;
    }

    @PostMapping("/{policyId}/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('" + PolicyRoles.MAKER + "')")
    PolicyVersionResponse createDraft(
            @PathVariable String policyId, @Valid @RequestBody CreateDraftRequest body, @AuthenticationPrincipal Jwt jwt) {
        PolicyRecord record = policyService.createDraft(policyId, body.yaml(), subject(jwt));
        return mapper.toResponse(record, true);
    }

    @PostMapping("/{policyId}/versions/{version}/submit")
    @PreAuthorize("hasRole('" + PolicyRoles.MAKER + "')")
    PolicyVersionResponse submit(
            @PathVariable String policyId, @PathVariable int version, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(policyService.submit(policyId, version, subject(jwt)), true);
    }

    @PostMapping("/{policyId}/versions/{version}/approve")
    @PreAuthorize("hasRole('" + PolicyRoles.CHECKER + "')")
    PolicyVersionResponse approve(
            @PathVariable String policyId, @PathVariable int version, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(policyService.approve(policyId, version, subject(jwt)), true);
    }

    @PostMapping("/{policyId}/versions/{version}/reject")
    @PreAuthorize("hasRole('" + PolicyRoles.CHECKER + "')")
    PolicyVersionResponse reject(
            @PathVariable String policyId, @PathVariable int version, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(policyService.reject(policyId, version, subject(jwt)), true);
    }

    @PostMapping("/{policyId}/versions/{version}/promote")
    @PreAuthorize("hasRole('" + PolicyRoles.OPS + "')")
    PolicyVersionResponse promote(
            @PathVariable String policyId,
            @PathVariable int version,
            @Valid @RequestBody PromoteRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(
                policyService.promote(policyId, version, body.target(), body.canaryPct(), subject(jwt)), true);
    }

    @PostMapping("/{policyId}/versions/{version}/rollback")
    @PreAuthorize("hasRole('" + PolicyRoles.OPS + "')")
    PolicyVersionResponse rollback(
            @PathVariable String policyId,
            @PathVariable int version,
            @RequestBody(required = false) RollbackRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        String reason = body != null ? body.reason() : null;
        return mapper.toResponse(policyService.rollback(policyId, version, subject(jwt), reason), true);
    }

    @GetMapping("/{policyId}/versions")
    @PreAuthorize("hasAnyRole('" + PolicyRoles.MAKER + "','" + PolicyRoles.CHECKER + "','" + PolicyRoles.OPS + "','"
            + PolicyRoles.AUDITOR + "')")
    List<PolicyVersionSummaryResponse> listVersions(@PathVariable String policyId) {
        return policyService.listVersions(policyId).stream().map(mapper::toSummary).toList();
    }

    @GetMapping("/{policyId}/versions/{version}")
    @PreAuthorize("hasAnyRole('" + PolicyRoles.MAKER + "','" + PolicyRoles.CHECKER + "','" + PolicyRoles.OPS + "','"
            + PolicyRoles.AUDITOR + "')")
    PolicyVersionResponse getVersion(@PathVariable String policyId, @PathVariable int version) {
        return mapper.toResponse(policyService.getVersion(policyId, version), true);
    }

    @GetMapping("/{policyId}/versions/{v1}/diff/{v2}")
    @PreAuthorize("hasAnyRole('" + PolicyRoles.MAKER + "','" + PolicyRoles.CHECKER + "','" + PolicyRoles.OPS + "','"
            + PolicyRoles.AUDITOR + "')")
    RuleDiffResponse diff(@PathVariable String policyId, @PathVariable("v1") int v1, @PathVariable("v2") int v2) {
        PolicyRecord left = policyService.getVersion(policyId, v1);
        PolicyRecord right = policyService.getVersion(policyId, v2);
        return mapper.toDiff(diffService.diff(left.yaml(), right.yaml()));
    }

    private static String subject(Jwt jwt) {
        return jwt.getSubject();
    }
}

package dev.hindsight.policy.api;

import dev.hindsight.policy.api.dto.PolicyVersionResponse;
import dev.hindsight.policy.api.dto.PolicyVersionSummaryResponse;
import dev.hindsight.policy.persistence.PolicyRecord;
import dev.hindsight.policy.service.PolicyRuleDiffService;
import org.springframework.stereotype.Component;

@Component
public class PolicyResponseMapper {

    public PolicyVersionResponse toResponse(PolicyRecord record, boolean includeYaml) {
        return new PolicyVersionResponse(
                record.policyId(),
                record.version(),
                record.contentHash(),
                record.status(),
                record.authorId(),
                record.approverId().orElse(null),
                record.canaryPct().orElse(null),
                record.createdAt(),
                record.approvedAt().orElse(null),
                includeYaml ? record.yaml() : null);
    }

    public PolicyVersionSummaryResponse toSummary(PolicyRecord record) {
        return new PolicyVersionSummaryResponse(
                record.policyId(),
                record.version(),
                record.contentHash(),
                record.status(),
                record.authorId(),
                record.createdAt(),
                record.approvedAt().orElse(null));
    }

    public dev.hindsight.policy.api.dto.RuleDiffResponse toDiff(PolicyRuleDiffService.RuleDiff diff) {
        return new dev.hindsight.policy.api.dto.RuleDiffResponse(
                diff.added(), diff.removed(), diff.changed(), diff.reordered());
    }
}

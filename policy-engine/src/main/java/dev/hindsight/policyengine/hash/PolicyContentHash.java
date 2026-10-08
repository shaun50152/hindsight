package dev.hindsight.policyengine.hash;

import dev.hindsight.common.hash.Sha256;
import dev.hindsight.common.json.CanonicalJson;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.model.PolicyHashDocument;

public final class PolicyContentHash {

    private PolicyContentHash() {}

    public static String hash(Policy policy) {
        byte[] canonical = CanonicalJson.toCanonicalBytes(PolicyHashDocument.from(policy));
        return Sha256.hex(canonical);
    }
}

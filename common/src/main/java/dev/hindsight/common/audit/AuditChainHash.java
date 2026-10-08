package dev.hindsight.common.audit;

import dev.hindsight.common.hash.Sha256;
import dev.hindsight.common.json.CanonicalJson;
import tools.jackson.databind.JsonNode;

/** hash = SHA-256(prevHash || canonical(payload)) as UTF-8 concatenation. */
public final class AuditChainHash {

    private AuditChainHash() {}

    public static String compute(String prevHash, JsonNode payload) {
        String canonical = CanonicalJson.toCanonicalString(payload);
        return Sha256.hex(prevHash + canonical);
    }
}

package dev.hindsight.decision.routing;

import dev.hindsight.common.hash.Sha256;

/** Pure stable customer bucket in {@code [0, 99]} for canary routing. */
public final class CustomerBucket {

    private CustomerBucket() {}

    public static int bucket0to99(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId required");
        }
        String hex = Sha256.hex("canary-bucket:" + customerId);
        long value = Long.parseUnsignedLong(hex.substring(0, 16), 16);
        return (int) (value % 100);
    }
}

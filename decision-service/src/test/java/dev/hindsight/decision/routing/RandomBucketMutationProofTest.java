package dev.hindsight.decision.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/**
 * Mutation proof: if bucketing used random assignment instead of SHA-256, the same customerId would
 * not always map to the same bucket (property fails under jqwik with enough trials).
 */
class RandomBucketMutationProofTest {

    static int randomBucket(String customerId) {
        return ThreadLocalRandom.current().nextInt(100);
    }

    @Test
    void randomBucketDiffersFromStableForSameCustomerEventually() {
        boolean sawDifference = false;
        for (int i = 0; i < 100; i++) {
            if (randomBucket("cust-x") != randomBucket("cust-x")) {
                sawDifference = true;
                break;
            }
        }
        assertThat(sawDifference).isTrue();
    }
}

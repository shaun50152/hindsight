package dev.hindsight.decision.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerBucketTest {

    @Test
    void bucketIsStableAndInRange() {
        int a = CustomerBucket.bucket0to99("cust-123");
        int b = CustomerBucket.bucket0to99("cust-123");
        assertThat(a).isEqualTo(b);
        assertThat(a).isBetween(0, 99);
    }
}

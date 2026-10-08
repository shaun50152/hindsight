package dev.hindsight.synthdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SyntheticApplicantGeneratorTest {

    @Test
    void sameSeedProducesSameApplicants() {
        var first = SyntheticApplicantGenerator.generate(20, 99L);
        var second = SyntheticApplicantGenerator.generate(20, 99L);
        assertThat(second).isEqualTo(first);
    }
}

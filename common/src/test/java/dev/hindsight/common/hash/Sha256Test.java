package dev.hindsight.common.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sha256Test {

    @Test
    void hashesEmptyStringToKnownVector() {
        // echo -n '' | sha256sum
        assertThat(Sha256.hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void hashesUtf8StringToKnownVector() {
        // echo -n 'abc' | sha256sum
        assertThat(Sha256.hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hexOfBytesMatchesHexOfString() {
        byte[] bytes = "hindsight".getBytes(StandardCharsets.UTF_8);
        assertThat(Sha256.hex(bytes)).isEqualTo(Sha256.hex("hindsight"));
    }

    @Test
    void digestLengthIs32Bytes() {
        assertThat(Sha256.digest("x".getBytes(StandardCharsets.UTF_8))).hasSize(32);
    }
}

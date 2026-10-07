package dev.hindsight.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalJsonTest {

    @Test
    void sortsObjectKeysLexicographically() {
        Map<String, Object> unordered = new LinkedHashMap<>();
        unordered.put("zeta", 1);
        unordered.put("alpha", 2);
        unordered.put("middle", 3);

        assertThat(CanonicalJson.toCanonicalString(unordered))
                .isEqualTo("{\"alpha\":2,\"middle\":3,\"zeta\":1}");
    }

    @Test
    void producesCompactJsonWithoutWhitespace() {
        Map<String, Object> value = Map.of(
                "nested", Map.of("b", true, "a", false),
                "items", List.of(1, 2, 3));

        String canonical = CanonicalJson.toCanonicalString(value);

        assertThat(canonical).doesNotContain(" ");
        assertThat(canonical).doesNotContain("\n");
        assertThat(canonical)
                .isEqualTo("{\"items\":[1,2,3],\"nested\":{\"a\":false,\"b\":true}}");
    }

    @Test
    void formatsNumbersStablyWithoutScientificNotation() {
        Map<String, Object> value = Map.of(
                "whole", new BigDecimal("10.0"),
                "fraction", new BigDecimal("0.50"),
                "large", new BigDecimal("1000000"));

        assertThat(CanonicalJson.toCanonicalString(value))
                .isEqualTo("{\"fraction\":0.5,\"large\":1000000,\"whole\":10}");
    }

    @Test
    void preservesArrayOrder() {
        Map<String, Object> value = Map.of("seq", List.of("c", "a", "b"));

        assertThat(CanonicalJson.toCanonicalString(value))
                .isEqualTo("{\"seq\":[\"c\",\"a\",\"b\"]}");
    }

    @Test
    void bytesMatchUtf8OfCanonicalString() {
        Map<String, Object> value = Map.of("x", "hello");
        assertThat(CanonicalJson.toCanonicalBytes(value))
                .isEqualTo(CanonicalJson.toCanonicalString(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

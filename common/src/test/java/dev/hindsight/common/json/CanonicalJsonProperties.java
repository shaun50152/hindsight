package dev.hindsight.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CanonicalJsonProperties {

    @Property
    void canonicalJsonIsStableUnderKeyReordering(
            @ForAll("jsonObjects") Map<String, Object> object) {
        String original = CanonicalJson.toCanonicalString(object);
        assertThat(CanonicalJson.toCanonicalString(reverseKeyOrder(object))).isEqualTo(original);
        assertThat(CanonicalJson.toCanonicalString(shuffleKeyOrder(object))).isEqualTo(original);
    }

    @Property
    void canonicalJsonIsDeterministicForSameLogicalMap(
            @ForAll("jsonObjects") Map<String, Object> object) {
        assertThat(CanonicalJson.toCanonicalString(object))
                .isEqualTo(CanonicalJson.toCanonicalString(object));
    }

    @Provide
    Arbitrary<Map<String, Object>> jsonObjects() {
        return jsonValue(2)
                .filter(Map.class::isInstance)
                .map(CanonicalJsonProperties::castMap);
    }

    private static Arbitrary<Object> jsonValue(int depth) {
        Arbitrary<Object> scalars = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.of(true, false),
                Arbitraries.integers().between(-10_000, 10_000).map(i -> i),
                Arbitraries.bigDecimals()
                        .between(new BigDecimal("-1000"), new BigDecimal("1000"))
                        .ofScale(4)
                        .map(bd -> bd),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8).map(s -> s));

        if (depth <= 0) {
            return scalars;
        }

        Arbitrary<Object> arrays = jsonValue(depth - 1)
                .list()
                .ofMinSize(0)
                .ofMaxSize(4)
                .map(list -> new ArrayList<>(list));

        Arbitrary<Object> objects = Arbitraries.maps(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(6),
                        jsonValue(depth - 1))
                .ofMinSize(1)
                .ofMaxSize(5)
                .map(LinkedHashMap::new);

        return Arbitraries.oneOf(scalars, arrays, objects);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> reverseKeyOrder(Map<String, Object> source) {
        List<String> keys = new ArrayList<>(source.keySet());
        Collections.reverse(keys);
        Map<String, Object> reversed = new LinkedHashMap<>();
        for (String key : keys) {
            reversed.put(key, maybeReorder(source.get(key)));
        }
        return reversed;
    }

    private static Map<String, Object> shuffleKeyOrder(Map<String, Object> source) {
        List<String> keys = new ArrayList<>(source.keySet());
        Collections.shuffle(keys);
        Map<String, Object> shuffled = new LinkedHashMap<>();
        for (String key : keys) {
            shuffled.put(key, maybeReorder(source.get(key)));
        }
        return shuffled;
    }

    private static Object maybeReorder(Object value) {
        if (value instanceof Map<?, ?> nested) {
            return shuffleKeyOrder(castMap(nested));
        }
        return value;
    }
}

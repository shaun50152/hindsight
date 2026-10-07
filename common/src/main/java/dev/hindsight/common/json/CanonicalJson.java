package dev.hindsight.common.json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic JSON serialization for content hashing.
 *
 * <p>Rules:
 * <ul>
 *   <li>Object keys are sorted lexicographically (Unicode code-point order)</li>
 *   <li>No insignificant whitespace</li>
 *   <li>Numbers use {@link BigDecimal#toPlainString()} after stripping trailing zeros
 *       (so {@code 1.0} becomes {@code 1}, never scientific notation)</li>
 *   <li>Array order is preserved</li>
 * </ul>
 */
public final class CanonicalJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CanonicalJson() {}

    public static String toCanonicalString(Object value) {
        return new String(toCanonicalBytes(value), StandardCharsets.UTF_8);
    }

    public static byte[] toCanonicalBytes(Object value) {
        JsonNode node = toNode(value);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator gen = MAPPER.createGenerator(out)) {
            write(node, gen);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static JsonNode toNode(Object value) {
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return MAPPER.valueToTree(value);
    }

    private static void write(JsonNode node, JsonGenerator gen) throws IOException {
        if (node == null || node.isNull()) {
            gen.writeNull();
            return;
        }
        if (node.isBoolean()) {
            gen.writeBoolean(node.booleanValue());
            return;
        }
        if (node.isNumber()) {
            writeNumber(node, gen);
            return;
        }
        if (node.isString()) {
            gen.writeString(node.stringValue());
            return;
        }
        if (node.isArray()) {
            gen.writeStartArray();
            for (JsonNode child : node) {
                write(child, gen);
            }
            gen.writeEndArray();
            return;
        }
        if (node.isObject()) {
            List<String> names = new ArrayList<>(node.propertyNames());
            Collections.sort(names);
            gen.writeStartObject();
            for (String name : names) {
                gen.writeName(name);
                write(node.get(name), gen);
            }
            gen.writeEndObject();
            return;
        }
        throw new IllegalArgumentException("Unsupported JSON node type: " + node.getNodeType());
    }

    private static void writeNumber(JsonNode node, JsonGenerator gen) throws IOException {
        BigDecimal value = node.decimalValue().stripTrailingZeros();
        // Avoid scientific notation; restore a non-negative scale for whole numbers.
        if (value.scale() < 0) {
            value = value.setScale(0);
        }
        gen.writeNumber(value);
    }
}

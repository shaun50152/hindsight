package dev.hindsight.policyengine.yaml;

import dev.hindsight.common.domain.ReasonCode;
import dev.hindsight.policyengine.model.Outcome;
import dev.hindsight.policyengine.model.Policy;
import dev.hindsight.policyengine.model.Rule;
import dev.hindsight.policyengine.model.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

public final class PolicyYamlParser {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private PolicyYamlParser() {}

    public record ParseResult(Policy policy, List<ValidationError> structureErrors) {}

    public static ParseResult parse(String yamlText) {
        JsonNode root;
        try {
            root = YAML.readTree(yamlText);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid YAML: " + e.getMessage(), e);
        }
        List<ValidationError> structureErrors = collectStructureErrors(root);
        Policy policy = mapPolicy(root);
        return new ParseResult(policy, structureErrors);
    }

    private static List<ValidationError> collectStructureErrors(JsonNode root) {
        List<ValidationError> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            errors.add(new ValidationError("", "policy document must be a YAML mapping"));
            return errors;
        }
        for (String name : root.propertyNames()) {
            if (!PolicyYamlConstants.TOP_LEVEL_KEYS.contains(name)) {
                errors.add(new ValidationError(name, "unknown field '" + name + "'"));
            }
        }
        JsonNode rules = root.get("rules");
        if (rules != null && rules.isArray()) {
            for (int i = 0; i < rules.size(); i++) {
                JsonNode rule = rules.get(i);
                if (rule != null && rule.isObject()) {
                    for (String name : rule.propertyNames()) {
                        if (!PolicyYamlConstants.RULE_KEYS.contains(name)) {
                            errors.add(new ValidationError(
                                    "rules[" + i + "]." + name, "unknown field '" + name + "'"));
                        }
                    }
                }
            }
        }
        return errors;
    }

    private static Policy mapPolicy(JsonNode root) {
        String policyId = textOrEmpty(root, "policyId");
        int version = root.path("version").asInt(0);
        String description = textOrEmpty(root, "description");
        String inputs = textOrEmpty(root, "inputs");
        Outcome defaultOutcome = parseOutcome(root.path("defaultOutcome").asString(null));
        List<Rule> rules = new ArrayList<>();
        JsonNode rulesNode = root.get("rules");
        if (rulesNode != null && rulesNode.isArray()) {
            for (JsonNode ruleNode : rulesNode) {
                rules.add(mapRule(ruleNode));
            }
        }
        return new Policy(policyId, version, description, inputs, defaultOutcome, List.copyOf(rules));
    }

    private static Rule mapRule(JsonNode node) {
        String id = textOrEmpty(node, "id");
        String when = stringifyWhen(node.get("when"));
        Outcome outcome = parseOutcome(node.path("outcome").asString(null));
        ReasonCode reason = parseReason(node.path("reason").asString(null));
        Optional<String> maxIncrease = Optional.empty();
        JsonNode maxNode = node.get("maxIncrease");
        if (maxNode != null && !maxNode.isNull()) {
            maxIncrease = Optional.of(stringifyWhen(maxNode));
        }
        return new Rule(id, when, outcome, reason, maxIncrease);
    }

    private static String stringifyWhen(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isBoolean()) {
            return Boolean.toString(node.booleanValue());
        }
        if (node.isNumber()) {
            return node.asString();
        }
        return node.asString("");
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asString("");
    }

    private static Outcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Outcome.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ReasonCode parseReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ReasonCode.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

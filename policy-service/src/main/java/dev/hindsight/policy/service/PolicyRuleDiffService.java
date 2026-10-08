package dev.hindsight.policy.service;

import dev.hindsight.policyengine.model.Rule;
import dev.hindsight.policyengine.yaml.PolicyYamlParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PolicyRuleDiffService {

    public RuleDiff diff(String yamlLeft, String yamlRight) {
        List<Rule> left = PolicyYamlParser.parse(yamlLeft).policy().rules();
        List<Rule> right = PolicyYamlParser.parse(yamlRight).policy().rules();
        Map<String, Rule> leftById = indexById(left);
        Map<String, Rule> rightById = indexById(right);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> reordered = new ArrayList<>();

        for (String id : rightById.keySet()) {
            if (!leftById.containsKey(id)) {
                added.add(id);
            }
        }
        for (String id : leftById.keySet()) {
            if (!rightById.containsKey(id)) {
                removed.add(id);
            }
        }
        for (String id : leftById.keySet()) {
            Rule r = rightById.get(id);
            if (r != null && !ruleContentEquals(leftById.get(id), r)) {
                changed.add(id);
            }
        }

        List<String> leftOrder = left.stream().map(Rule::id).filter(leftById::containsKey).toList();
        List<String> rightOrder = right.stream().map(Rule::id).filter(rightById::containsKey).toList();
        Set<String> shared = new LinkedHashSet<>(leftById.keySet());
        shared.retainAll(rightById.keySet());
        List<String> leftSharedOrder = leftOrder.stream().filter(shared::contains).toList();
        List<String> rightSharedOrder = rightOrder.stream().filter(shared::contains).toList();
        if (!leftSharedOrder.equals(rightSharedOrder)) {
            for (String id : shared) {
                if (leftSharedOrder.indexOf(id) != rightSharedOrder.indexOf(id)
                        && !changed.contains(id)
                        && leftById.get(id).equals(rightById.get(id))) {
                    reordered.add(id);
                }
            }
        }

        return new RuleDiff(List.copyOf(added), List.copyOf(removed), List.copyOf(changed), List.copyOf(reordered));
    }

    private static Map<String, Rule> indexById(List<Rule> rules) {
        Map<String, Rule> map = new LinkedHashMap<>();
        for (Rule rule : rules) {
            map.put(rule.id(), rule);
        }
        return map;
    }

    private static boolean ruleContentEquals(Rule a, Rule b) {
        return a.id().equals(b.id())
                && a.when().equals(b.when())
                && a.outcome() == b.outcome()
                && a.reason() == b.reason()
                && a.maxIncrease().equals(b.maxIncrease());
    }

    public record RuleDiff(List<String> added, List<String> removed, List<String> changed, List<String> reordered) {}
}

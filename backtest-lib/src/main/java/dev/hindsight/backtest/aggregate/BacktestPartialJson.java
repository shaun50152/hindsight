package dev.hindsight.backtest.aggregate;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class BacktestPartialJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private BacktestPartialJson() {}

    public static String toJson(BacktestPartialAggregate aggregate) {
        try {
            return JSON.writeValueAsString(toNode(aggregate));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize partial aggregate", e);
        }
    }

    public static BacktestPartialAggregate fromJson(String json) {
        try {
            return fromNode(JSON.readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid partial aggregate JSON", e);
        }
    }

    private static ObjectNode toNode(BacktestPartialAggregate aggregate) {
        ObjectNode root = JSON.createObjectNode();
        root.put("totalDecisions", aggregate.totalDecisions());
        root.put("candidateApprovals", aggregate.candidateApprovals());
        root.put("recordedApprovals", aggregate.recordedApprovals());
        root.put("flipCount", aggregate.flipCount());
        root.put("exposureDelta", aggregate.exposureDelta());
        root.set("flipMatrix", matrixNode(aggregate.flipMatrix()));
        root.set("utilizationBaselineBins", binsNode(aggregate.utilizationBaselineBins()));
        root.set("utilizationFlipBins", binsNode(aggregate.utilizationFlipBins()));
        root.set("ficoBaselineBins", binsNode(aggregate.ficoBaselineBins()));
        root.set("ficoFlipBins", binsNode(aggregate.ficoFlipBins()));
        ObjectNode segments = JSON.createObjectNode();
        for (Map.Entry<String, BacktestPartialAggregate.SegmentPartial> entry : aggregate.segments().entrySet()) {
            ObjectNode seg = JSON.createObjectNode();
            seg.put("total", entry.getValue().total());
            seg.put("flipsToDecline", entry.getValue().flipsToDecline());
            segments.set(entry.getKey(), seg);
        }
        root.set("segments", segments);
        return root;
    }

    private static BacktestPartialAggregate fromNode(JsonNode root) {
        long[][] matrix = new long[3][3];
        JsonNode matrixNode = root.get("flipMatrix");
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                matrix[i][j] = matrixNode.get(i).get(j).longValue();
            }
        }
        Map<String, BacktestPartialAggregate.SegmentPartial> segments = new LinkedHashMap<>();
        JsonNode segmentsNode = root.get("segments");
        segmentsNode.properties().forEach(entry -> {
            JsonNode seg = entry.getValue();
            segments.put(
                    entry.getKey(),
                    new BacktestPartialAggregate.SegmentPartial(
                            seg.get("total").longValue(), seg.get("flipsToDecline").longValue()));
        });
        return new BacktestPartialAggregate(
                root.get("totalDecisions").longValue(),
                root.get("candidateApprovals").longValue(),
                root.get("recordedApprovals").longValue(),
                root.get("flipCount").longValue(),
                matrix,
                root.get("exposureDelta").doubleValue(),
                segments,
                readBins(root.get("utilizationBaselineBins")),
                readBins(root.get("utilizationFlipBins")),
                readBins(root.get("ficoBaselineBins")),
                readBins(root.get("ficoFlipBins")));
    }

    private static ArrayNode matrixNode(long[][] matrix) {
        ArrayNode outer = JSON.createArrayNode();
        for (int i = 0; i < 3; i++) {
            ArrayNode inner = JSON.createArrayNode();
            for (int j = 0; j < 3; j++) {
                inner.add(matrix[i][j]);
            }
            outer.add(inner);
        }
        return outer;
    }

    private static ArrayNode binsNode(long[] bins) {
        ArrayNode node = JSON.createArrayNode();
        for (long bin : bins) {
            node.add(bin);
        }
        return node;
    }

    private static long[] readBins(JsonNode node) {
        long[] bins = new long[node.size()];
        for (int i = 0; i < node.size(); i++) {
            bins[i] = node.get(i).longValue();
        }
        return bins;
    }
}

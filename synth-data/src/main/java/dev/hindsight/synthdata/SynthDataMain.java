package dev.hindsight.synthdata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class SynthDataMain {

    private SynthDataMain() {}

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        String token = System.getenv("HINDSIGHT_JWT_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("HINDSIGHT_JWT_TOKEN env var is required");
        }
        List<SyntheticApplicantGenerator.SyntheticApplicant> applicants =
                SyntheticApplicantGenerator.generate(config.count(), config.seed());
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        long intervalNanos = config.ratePerSecond() <= 0
                ? 0
                : 1_000_000_000L / config.ratePerSecond();
        for (int i = 0; i < applicants.size(); i++) {
            SyntheticApplicantGenerator.SyntheticApplicant a = applicants.get(i);
            String body = requestJson(config.policyId(), "synth-req-" + config.seed() + "-" + i, a);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl().replaceAll("/$", "") + "/v1/decisions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                System.err.println("Request failed status=" + response.statusCode() + " body=" + response.body());
            }
            if (intervalNanos > 0 && i + 1 < applicants.size()) {
                Thread.sleep(intervalNanos / 1_000_000L, (int) (intervalNanos % 1_000_000L));
            }
        }
    }

    static String requestJson(
            String policyId, String requestId, SyntheticApplicantGenerator.SyntheticApplicant a) {
        String policyField = policyId == null || policyId.isBlank()
                ? ""
                : "\"policyId\": \"" + policyId + "\",\n";
        return """
                {
                  "requestId": "%s",
                  %s  "applicant": {
                    "customerId": "%s",
                    "currentLimit": %s,
                    "requestedIncrease": %s,
                    "utilization": %s,
                    "delinquencies12m": %d,
                    "tenureMonths": %d,
                    "ficoBand": %d,
                    "incomeBand": "%s",
                    "incomeVerified": %s,
                    "segment": "%s",
                    "asOf": "%s"
                  }
                }
                """
                .formatted(
                        requestId,
                        policyField,
                        a.customerId(),
                        a.currentLimit(),
                        a.requestedIncrease(),
                        a.utilization(),
                        a.delinquencies12m(),
                        a.tenureMonths(),
                        a.ficoBand(),
                        a.incomeBand(),
                        a.incomeVerified(),
                        a.segment(),
                        a.asOf());
    }

    record Config(int count, int ratePerSecond, String baseUrl, String policyId, long seed) {

        static Config parse(String[] args) {
            int count = 10;
            int rate = 5;
            String baseUrl = "http://localhost:8082";
            String policyId = null;
            long seed = 42L;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--count" -> count = Integer.parseInt(args[++i]);
                    case "--rate" -> rate = Integer.parseInt(args[++i]);
                    case "--base-url" -> baseUrl = args[++i];
                    case "--policy-id" -> policyId = args[++i];
                    case "--seed" -> seed = Long.parseLong(args[++i]);
                    default -> throw new IllegalArgumentException("Unknown arg: " + args[i]);
                }
            }
            return new Config(count, rate, baseUrl, policyId, seed);
        }
    }
}

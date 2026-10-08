package dev.hindsight.decision.guardrail;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hindsight.guardrail")
public class GuardrailProperties {

    private boolean enabled = true;
    private Duration windowDuration = Duration.ofMinutes(5);
    private int minSampleSize = 30;
    private Duration cooldownDuration = Duration.ofMinutes(10);
    private long evaluationIntervalMs = 5000;
    private String policyServiceBaseUrl = "http://localhost:8081";
    private String jwt = "";
    private Thresholds defaultThresholds = new Thresholds();
    private Map<String, Thresholds> policies = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }

    public void setWindowDuration(Duration windowDuration) {
        this.windowDuration = windowDuration;
    }

    public int getMinSampleSize() {
        return minSampleSize;
    }

    public void setMinSampleSize(int minSampleSize) {
        this.minSampleSize = minSampleSize;
    }

    public Duration getCooldownDuration() {
        return cooldownDuration;
    }

    public void setCooldownDuration(Duration cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
    }

    public long getEvaluationIntervalMs() {
        return evaluationIntervalMs;
    }

    public void setEvaluationIntervalMs(long evaluationIntervalMs) {
        this.evaluationIntervalMs = evaluationIntervalMs;
    }

    public String getPolicyServiceBaseUrl() {
        return policyServiceBaseUrl;
    }

    public void setPolicyServiceBaseUrl(String policyServiceBaseUrl) {
        this.policyServiceBaseUrl = policyServiceBaseUrl;
    }

    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
    }

    public Thresholds getDefaultThresholds() {
        return defaultThresholds;
    }

    public void setDefaultThresholds(Thresholds defaultThresholds) {
        this.defaultThresholds = defaultThresholds;
    }

    public Map<String, Thresholds> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, Thresholds> policies) {
        this.policies = policies;
    }

    public Thresholds thresholdsFor(String policyId) {
        return policies.getOrDefault(policyId, defaultThresholds);
    }

    public static class Thresholds {
        private double maxApprovalRateDelta = 0.15;
        private double maxShadowFlipRate = 0.50;
        private double maxErrorRate = 0.05;
        private long maxP99LatencyMs = 500;

        public double getMaxApprovalRateDelta() {
            return maxApprovalRateDelta;
        }

        public void setMaxApprovalRateDelta(double maxApprovalRateDelta) {
            this.maxApprovalRateDelta = maxApprovalRateDelta;
        }

        public double getMaxShadowFlipRate() {
            return maxShadowFlipRate;
        }

        public void setMaxShadowFlipRate(double maxShadowFlipRate) {
            this.maxShadowFlipRate = maxShadowFlipRate;
        }

        public double getMaxErrorRate() {
            return maxErrorRate;
        }

        public void setMaxErrorRate(double maxErrorRate) {
            this.maxErrorRate = maxErrorRate;
        }

        public long getMaxP99LatencyMs() {
            return maxP99LatencyMs;
        }

        public void setMaxP99LatencyMs(long maxP99LatencyMs) {
            this.maxP99LatencyMs = maxP99LatencyMs;
        }
    }
}

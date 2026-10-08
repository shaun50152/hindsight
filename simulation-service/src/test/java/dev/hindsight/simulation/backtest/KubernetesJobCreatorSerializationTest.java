package dev.hindsight.simulation.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Isolates fabric8 Job JSON/YAML serialization from the kubernetes-server mock. */
class KubernetesJobCreatorSerializationTest {

    private static KubernetesJobCreator creator() {
        return new KubernetesJobCreator(
                null,
                "hindsight",
                "hindsight/backtest-worker:test",
                "backtest-worker",
                300,
                1,
                "100m",
                "500m",
                "256Mi",
                "512Mi",
                "kafka:9092",
                "jdbc:postgresql://db/simulation",
                "jdbc:postgresql://db/policy",
                "user",
                "pass",
                "decision.made");
    }

    @Test
    void fabric8SerializationIncludesIndexedJobFields() {
        UUID backtestId = UUID.fromString("00000000-0000-0000-0000-00000000dead");
        Job job = creator().buildJob("backtest-deadbeef", backtestId, 3, "abc123", 1);

        assertThatCode(() -> Serialization.asJson(job)).doesNotThrowAnyException();
        assertThatCode(() -> Serialization.asYaml(job)).doesNotThrowAnyException();
        String json = Serialization.asJson(job);
        String yaml = Serialization.asYaml(job);

        for (String serialized : new String[] {json, yaml}) {
            assertThat(serialized).contains("completionMode");
            assertThat(serialized).contains("Indexed");
            assertThat(serialized).contains("parallelism");
            assertThat(serialized).contains("ttlSecondsAfterFinished");
            assertThat(serialized).contains("securityContext");
            assertThat(serialized).contains("runAsNonRoot");
            assertThat(serialized).contains("readOnlyRootFilesystem");
        }
    }
}

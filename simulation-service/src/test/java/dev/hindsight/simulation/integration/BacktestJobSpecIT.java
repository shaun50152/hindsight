package dev.hindsight.simulation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hindsight.simulation.backtest.KubernetesJobCreator;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@EnableKubernetesMockClient(crud = true, https = true)
class BacktestJobSpecIT {

    KubernetesClient client;

    @BeforeEach
    void ensureNamespace() {
        client.namespaces()
                .resource(new NamespaceBuilder()
                        .withNewMetadata()
                        .withName("hindsight")
                        .endMetadata()
                        .build())
                .create();
    }

    private static KubernetesJobCreator jobCreator(KubernetesClient client) {
        return new KubernetesJobCreator(
                client,
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
    void mockServerCrudModeServesNamespaceApi() {
        assertThat(client.namespaces().withName("hindsight").get()).isNotNull();
        assertThat(client.namespaces().list().getItems()).anyMatch(ns -> "hindsight".equals(ns.getMetadata().getName()));
    }

    @Test
    void createJobPostsIndexedJobToMockServer() {
        KubernetesJobCreator creator = jobCreator(client);
        UUID backtestId = UUID.randomUUID();
        String jobName = creator.createJob(backtestId, 3, "abc123", 1);

        Job stored = client.batch().v1().jobs().inNamespace("hindsight").withName(jobName).get();
        assertThat(stored).isNotNull();
        assertThat(stored.getSpec().getCompletionMode()).isEqualTo("Indexed");
        assertThat(stored.getSpec().getParallelism()).isEqualTo(3);
        assertThat(stored.getSpec().getTtlSecondsAfterFinished()).isEqualTo(300);

        var container = stored.getSpec().getTemplate().getSpec().getContainers().getFirst();
        assertThat(container.getSecurityContext().getRunAsNonRoot()).isTrue();
        assertThat(container.getSecurityContext().getReadOnlyRootFilesystem()).isTrue();
    }

    @Test
    void indexedJobTemplateMatchesSpec() {
        KubernetesJobCreator creator = jobCreator(null);

        UUID backtestId = UUID.randomUUID();
        Job job = creator.buildJob("backtest-deadbeef", backtestId, 3, "abc123", 1);

        assertThat(job.getSpec().getCompletionMode()).isEqualTo("Indexed");
        assertThat(job.getSpec().getCompletions()).isEqualTo(3);
        assertThat(job.getSpec().getParallelism()).isEqualTo(3);
        assertThat(job.getSpec().getTtlSecondsAfterFinished()).isEqualTo(300);
        assertThat(job.getSpec().getBackoffLimit()).isEqualTo(1);
        assertThat(job.getSpec().getTemplate().getSpec().getServiceAccountName()).isEqualTo("backtest-worker");

        var container = job.getSpec().getTemplate().getSpec().getContainers().getFirst();
        assertThat(container.getImage()).isEqualTo("hindsight/backtest-worker:test");
        assertThat(container.getResources().getRequests()).containsKeys("cpu", "memory");
        assertThat(container.getResources().getLimits()).containsKeys("cpu", "memory");
        assertThat(container.getSecurityContext().getRunAsNonRoot()).isTrue();
        assertThat(container.getSecurityContext().getReadOnlyRootFilesystem()).isTrue();
        assertThat(container.getSecurityContext().getCapabilities().getDrop()).contains("ALL");

        assertThat(container.getEnv())
                .anyMatch(env -> "BACKTEST_ID".equals(env.getName()) && backtestId.toString().equals(env.getValue()));
        assertThat(container.getEnv()).anyMatch(env -> "CANDIDATE_CONTENT_HASH".equals(env.getName()));
        assertThat(container.getEnv()).anyMatch(env -> "JOB_COMPLETION_INDEX".equals(env.getName()));
    }
}

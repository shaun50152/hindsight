package dev.hindsight.simulation.backtest;

import io.fabric8.kubernetes.api.model.CapabilitiesBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectFieldSelectorBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(io.fabric8.kubernetes.client.KubernetesClient.class)
public class KubernetesJobCreator {

    private final KubernetesClient kubernetesClient;
    private final String namespace;
    private final String workerImage;
    private final String serviceAccountName;
    private final int ttlSecondsAfterFinished;
    private final int backoffLimit;
    private final String cpuRequest;
    private final String cpuLimit;
    private final String memoryRequest;
    private final String memoryLimit;
    private final String kafkaBootstrap;
    private final String simulationDbUrl;
    private final String policyDbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String decisionTopic;

    public KubernetesJobCreator(
            KubernetesClient kubernetesClient,
            @Value("${hindsight.backtest.kubernetes.namespace}") String namespace,
            @Value("${hindsight.backtest.kubernetes.worker-image}") String workerImage,
            @Value("${hindsight.backtest.kubernetes.service-account-name}") String serviceAccountName,
            @Value("${hindsight.backtest.kubernetes.ttl-seconds-after-finished}") int ttlSecondsAfterFinished,
            @Value("${hindsight.backtest.kubernetes.backoff-limit}") int backoffLimit,
            @Value("${hindsight.backtest.kubernetes.cpu-request}") String cpuRequest,
            @Value("${hindsight.backtest.kubernetes.cpu-limit}") String cpuLimit,
            @Value("${hindsight.backtest.kubernetes.memory-request}") String memoryRequest,
            @Value("${hindsight.backtest.kubernetes.memory-limit}") String memoryLimit,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrap,
            @Value("${spring.datasource.url}") String simulationDbUrl,
            @Value("${hindsight.backtest.policy-datasource.url}") String policyDbUrl,
            @Value("${spring.datasource.username}") String dbUser,
            @Value("${spring.datasource.password}") String dbPassword,
            @Value("${hindsight.backtest.decision-topic}") String decisionTopic) {
        this.kubernetesClient = kubernetesClient;
        this.namespace = namespace;
        this.workerImage = workerImage;
        this.serviceAccountName = serviceAccountName;
        this.ttlSecondsAfterFinished = ttlSecondsAfterFinished;
        this.backoffLimit = backoffLimit;
        this.cpuRequest = cpuRequest;
        this.cpuLimit = cpuLimit;
        this.memoryRequest = memoryRequest;
        this.memoryLimit = memoryLimit;
        this.kafkaBootstrap = kafkaBootstrap;
        this.simulationDbUrl = simulationDbUrl;
        this.policyDbUrl = policyDbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.decisionTopic = decisionTopic;
    }

    public String createJob(UUID backtestId, int shardCount, String candidateContentHash, int samplingStride) {
        String jobName = "backtest-" + backtestId.toString().substring(0, 8);
        Job job = buildJob(jobName, backtestId, shardCount, candidateContentHash, samplingStride);
        kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(job).create();
        return jobName;
    }

    public Job buildJob(String jobName, UUID backtestId, int shardCount, String candidateContentHash, int samplingStride) {
        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withCompletionMode("Indexed")
                .withCompletions(shardCount)
                .withParallelism(shardCount)
                .withBackoffLimit(backoffLimit)
                .withTtlSecondsAfterFinished(ttlSecondsAfterFinished)
                .withNewTemplate()
                .withNewSpec()
                .withServiceAccountName(serviceAccountName)
                .withRestartPolicy("Never")
                .withContainers(new ContainerBuilder()
                        .withName("backtest-worker")
                        .withImage(workerImage)
                        .withEnv(
                                new EnvVarBuilder()
                                        .withName("BACKTEST_ID")
                                        .withValue(backtestId.toString())
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("CANDIDATE_CONTENT_HASH")
                                        .withValue(candidateContentHash)
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("SAMPLING_STRIDE")
                                        .withValue(String.valueOf(samplingStride))
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("HINDSIGHT_SIMULATION_DB_URL")
                                        .withValue(simulationDbUrl)
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("HINDSIGHT_POLICY_DB_URL")
                                        .withValue(policyDbUrl)
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("HINDSIGHT_DB_USER")
                                        .withValue(dbUser)
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("HINDSIGHT_DB_PASSWORD")
                                        .withValue(dbPassword)
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("HINDSIGHT_KAFKA_BOOTSTRAP")
                                        .withValue(kafkaBootstrap)
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("JOB_COMPLETION_INDEX")
                                        .withValueFrom(new EnvVarSourceBuilder()
                                                .withFieldRef(new ObjectFieldSelectorBuilder()
                                                        .withFieldPath(
                                                                "metadata.annotations['batch.kubernetes.io/job-completion-index']")
                                                        .build())
                                                .build())
                                        .build())
                        .withResources(new ResourceRequirementsBuilder()
                                .addToRequests("cpu", new Quantity(cpuRequest))
                                .addToRequests("memory", new Quantity(memoryRequest))
                                .addToLimits("cpu", new Quantity(cpuLimit))
                                .addToLimits("memory", new Quantity(memoryLimit))
                                .build())
                        .withSecurityContext(new SecurityContextBuilder()
                                .withRunAsNonRoot(true)
                                .withReadOnlyRootFilesystem(true)
                                .withAllowPrivilegeEscalation(false)
                                .withCapabilities(new CapabilitiesBuilder()
                                        .withDrop("ALL")
                                        .build())
                                .build())
                        .build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    public Job getJob(String namespace, String jobName) {
        return kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
    }
}

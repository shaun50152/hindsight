package dev.hindsight.backtest;

import dev.hindsight.backtest.aggregate.BacktestPartialAggregate;
import dev.hindsight.backtest.evaluate.BacktestShardEvaluator;
import dev.hindsight.backtest.kafka.KafkaDecisionSource;
import dev.hindsight.backtest.persistence.BacktestPartialRepository;
import dev.hindsight.backtest.persistence.BacktestShardPlanRepository;
import dev.hindsight.backtest.policy.PolicyByHashLoader;
import dev.hindsight.backtest.source.DecisionSource;
import dev.hindsight.policyengine.model.CompiledPolicy;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hindsight.backtest.worker-enabled", havingValue = "true", matchIfMissing = true)
public class BacktestWorkerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BacktestWorkerRunner.class);

    private final BacktestPartialRepository partialRepository;
    private final BacktestShardPlanRepository shardPlanRepository;
    private final DataSource policyDataSource;
    private final String backtestIdRaw;
    private final int shardIndex;
    private final String candidateContentHash;
    private final String kafkaBootstrap;
    private final String decisionTopic;

    public BacktestWorkerRunner(
            BacktestPartialRepository partialRepository,
            BacktestShardPlanRepository shardPlanRepository,
            @Qualifier("policyDataSource") DataSource policyDataSource,
            @Value("${hindsight.backtest.backtest-id}") String backtestIdRaw,
            @Value("${hindsight.backtest.shard-index}") int shardIndex,
            @Value("${hindsight.backtest.candidate-content-hash}") String candidateContentHash,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrap,
            @Value("${hindsight.backtest.decision-topic}") String decisionTopic) {
        this.partialRepository = partialRepository;
        this.shardPlanRepository = shardPlanRepository;
        this.policyDataSource = policyDataSource;
        this.backtestIdRaw = backtestIdRaw;
        this.shardIndex = shardIndex;
        this.candidateContentHash = candidateContentHash;
        this.kafkaBootstrap = kafkaBootstrap;
        this.decisionTopic = decisionTopic;
    }

    @Override
    public void run(String... args) {
        if (backtestIdRaw == null || backtestIdRaw.isBlank()) {
            log.error("BACKTEST_ID is required");
            System.exit(1);
            return;
        }
        if (candidateContentHash == null || candidateContentHash.isBlank()) {
            log.error("CANDIDATE_CONTENT_HASH is required");
            System.exit(1);
            return;
        }
        UUID backtestId = UUID.fromString(backtestIdRaw);
        try {
            CompiledPolicy policy = PolicyByHashLoader.loadFromPolicySchema(policyDataSource, candidateContentHash);
            BacktestShardPlanRepository.ShardPlanRow plan = shardPlanRepository.load(backtestId, shardIndex);
            String groupId = "backtest-worker-" + backtestId + "-" + shardIndex;
            DecisionSource source = new KafkaDecisionSource(
                    kafkaBootstrap, decisionTopic, groupId, plan.assignments());
            BacktestPartialAggregate partial =
                    BacktestShardEvaluator.evaluate(policy, source, plan.samplingStride());
            partialRepository.upsert(backtestId, shardIndex, partial);
            log.info(
                    "Backtest shard complete backtestId={} shard={} decisions={}",
                    backtestId,
                    shardIndex,
                    partial.totalDecisions());
            System.exit(0);
        } catch (Exception e) {
            log.error("Backtest shard failed backtestId={} shard={}", backtestId, shardIndex, e);
            System.exit(1);
        }
    }
}

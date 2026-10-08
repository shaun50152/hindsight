package dev.hindsight.simulation.backtest;

import dev.hindsight.backtest.aggregate.BacktestPartialAggregate;
import dev.hindsight.backtest.evaluate.BacktestShardEvaluator;
import dev.hindsight.backtest.kafka.KafkaDecisionSource;
import dev.hindsight.backtest.policy.PolicyByHashLoader;
import dev.hindsight.policyengine.model.CompiledPolicy;
import dev.hindsight.simulation.persistence.BacktestPartialRepository;
import dev.hindsight.simulation.persistence.BacktestRepository;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BacktestShardExecutor {

    private final BacktestPartialRepository partialRepository;
    private final DataSource policyDataSource;
    private final String kafkaBootstrap;
    private final String decisionTopic;

    public BacktestShardExecutor(
            BacktestPartialRepository partialRepository,
            @Qualifier("policyDataSource") DataSource policyDataSource,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrap,
            @Value("${hindsight.backtest.decision-topic}") String decisionTopic) {
        this.partialRepository = partialRepository;
        this.policyDataSource = policyDataSource;
        this.kafkaBootstrap = kafkaBootstrap;
        this.decisionTopic = decisionTopic;
    }

    public void runShard(BacktestRepository.BacktestRow backtest, int shardIndex) {
        CompiledPolicy policy =
                PolicyByHashLoader.loadFromPolicySchema(policyDataSource, backtest.candidateContentHash());
        ShardPlanner.ShardPlanEntry plan = backtest.shardPlan().stream()
                .filter(entry -> entry.shardIndex() == shardIndex)
                .findFirst()
                .orElseThrow();
        String groupId = "backtest-local-" + backtest.id() + "-" + shardIndex;
        KafkaDecisionSource source = new KafkaDecisionSource(
                kafkaBootstrap, decisionTopic, groupId, plan.assignments());
        BacktestPartialAggregate partial =
                BacktestShardEvaluator.evaluate(policy, source, backtest.samplingStride());
        partialRepository.upsert(backtest.id(), shardIndex, partial);
    }
}

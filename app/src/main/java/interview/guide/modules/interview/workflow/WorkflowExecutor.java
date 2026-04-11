package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncCommandAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.service.InterviewStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 面试工作流执行器
 * 支持 checkpoint（检查点）和 resume（恢复）功能
 *
 * 工作流执行流程：
 * 1. /current 接口 → entry → question_generator → [中断等待答案]
 * 2. /answer 接口 → scorer → decider → [ASK] question_generator → [中断等待答案]
 *                                  → [SWITCH] role_switcher → question_generator → [中断等待答案]
 *                                  → [FINISH] final_reporter → [完成]
 */
@Slf4j
@Component
public class WorkflowExecutor {

    private final EntryNode entryNode;
    private final QuestionGeneratorNode questionGeneratorNode;
    private final ScorerNode scorerNode;
    private final DeciderNode deciderNode;
    private final RoleSwitcherNode roleSwitcherNode;
    private final FinalReporterNode finalReporterNode;
    private final SearchPreparatorNode searchPreparatorNode;
    private final InterviewStreamService interviewStreamService;
    private final RedisService redisService;

    private CompiledGraph compiledGraph;
    private BaseCheckpointSaver redisSaver;

    /**
     * 工作流图节点名称
     */
    private static final String NODE_ENTRY = "entry";
    private static final String NODE_QUESTION_GENERATOR = "question_generator";
    private static final String NODE_SCORER = "scorer";
    private static final String NODE_DECIDER = "decider";
    private static final String NODE_ROLE_SWITCHER = "role_switcher";
    private static final String NODE_FINAL_REPORTER = "final_reporter";
    private static final String NODE_SEARCH_PREPARATOR = "search_preparator";

    /**
     * Redis 中存储工作流检查点的 key 前缀
     */
    private static final String WORKFLOW_CHECKPOINT_PREFIX = "workflow:checkpoint:";

    public WorkflowExecutor(EntryNode entryNode,
                           QuestionGeneratorNode questionGeneratorNode,
                           ScorerNode scorerNode,
                           DeciderNode deciderNode,
                           RoleSwitcherNode roleSwitcherNode,
                           FinalReporterNode finalReporterNode,
                           SearchPreparatorNode searchPreparatorNode,
                           InterviewStreamService interviewStreamService,
                           RedisService redisService) {
        this.entryNode = entryNode;
        this.questionGeneratorNode = questionGeneratorNode;
        this.scorerNode = scorerNode;
        this.deciderNode = deciderNode;
        this.roleSwitcherNode = roleSwitcherNode;
        this.finalReporterNode = finalReporterNode;
        this.searchPreparatorNode = searchPreparatorNode;
        this.interviewStreamService = interviewStreamService;
        this.redisService = redisService;
    }

    /**
     * 在 PostConstruct 中构建状态图
     */
    @PostConstruct
    public void buildWorkflowGraph() {
        log.info("Building interview workflow graph with checkpoint support...");

        try {
            // 创建 KeyStrategyFactory - 使用 REPLACE 策略覆盖所有值
            KeyStrategyFactory keyStrategyFactory = () -> {
                Map<String, KeyStrategy> strategies = new HashMap<>();
                // 显式注册关键 key，确保框架能识别
                strategies.put(InterviewWorkflowState.SESSION_ID, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.CURRENT_QUESTION_INDEX, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.SCORE, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.FEEDBACK, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.ADJUSTED_DIFFICULTY, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.CURRENT_PERSPECTIVE_ID, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.CREATED_BY_PERSPECTIVE_ID, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.CREATED_BY_PERSPECTIVE_NAME, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.NEXT_PERSPECTIVE_ID, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.DECISION_ACTION, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.DECISION_REASON, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.CURRENT_QUESTION, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.CURRENT_CATEGORY, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.CURRENT_DIFFICULTY, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.IS_COMPLETE, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.CURRENT_ANSWER, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.SEARCH_ENABLED, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.SEARCH_RESULT, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.SEARCH_KEYWORDS, KeyStrategy.REPLACE);
                strategies.put(InterviewWorkflowState.SEARCH_DECISION_REASON, KeyStrategy.REPLACE);
                return strategies;
            };

            // 创建状态图
            StateGraph stateGraph = new StateGraph(keyStrategyFactory);

            // 添加节点 - 使用 AsyncNodeAction.node_async 将同步 NodeAction 转换为异步
            stateGraph.addNode(NODE_ENTRY, AsyncNodeAction.node_async(adaptNodeAction(entryNode::execute)));
            stateGraph.addNode(NODE_QUESTION_GENERATOR, AsyncNodeAction.node_async(adaptNodeAction(questionGeneratorNode::execute)));
            stateGraph.addNode(NODE_SCORER, AsyncNodeAction.node_async(adaptNodeAction(scorerNode::execute)));
            stateGraph.addNode(NODE_DECIDER, AsyncNodeAction.node_async(adaptNodeAction(deciderNode::execute)));
            stateGraph.addNode(NODE_ROLE_SWITCHER, AsyncNodeAction.node_async(adaptNodeAction(roleSwitcherNode::execute)));
            stateGraph.addNode(NODE_FINAL_REPORTER, AsyncNodeAction.node_async(adaptNodeAction(finalReporterNode::execute)));
            stateGraph.addNode(NODE_SEARCH_PREPARATOR, AsyncNodeAction.node_async(adaptNodeAction(searchPreparatorNode::execute)));

            // 添加普通边 - START -> entry 是隐式入口
            stateGraph.addEdge(StateGraph.START, NODE_ENTRY);
            stateGraph.addEdge(NODE_ENTRY, NODE_QUESTION_GENERATOR);
            stateGraph.addEdge(NODE_QUESTION_GENERATOR, NODE_SCORER);
            stateGraph.addEdge(NODE_SCORER, NODE_DECIDER);
            stateGraph.addEdge(NODE_ROLE_SWITCHER, NODE_SEARCH_PREPARATOR);
            stateGraph.addEdge(NODE_FINAL_REPORTER, StateGraph.END);

            // 添加条件边 - decider 根据决策结果决定下一步
            Map<String, String> edgeMapping = new HashMap<>();
            edgeMapping.put(DecisionAction.ASK.name(), NODE_SEARCH_PREPARATOR);
            edgeMapping.put(DecisionAction.SWITCH.name(), NODE_ROLE_SWITCHER);
            edgeMapping.put(DecisionAction.FINISH.name(), NODE_FINAL_REPORTER);

            // 使用 AsyncCommandAction.of 将 AsyncEdgeAction 转换为 AsyncCommandAction
            stateGraph.addConditionalEdges(NODE_DECIDER, AsyncCommandAction.of(createDeciderAsyncEdgeAction()), edgeMapping);

            // search_decider 决定下一步（Web搜索已集成到 QuestionGeneratorNode 中的 HybridSearchService）
            // searchEnabled 只用于控制是否执行 Web 搜索，不再作为独立节点
            stateGraph.addEdge(NODE_SEARCH_PREPARATOR, NODE_QUESTION_GENERATOR);

            // 创建 Redis Saver
            this.redisSaver = new RedisSaver(redisService.getClient());

            // 创建 SaverConfig
            SaverConfig saverConfig = SaverConfig.builder()
                    .register(WORKFLOW_CHECKPOINT_PREFIX, redisSaver)
                    .build();

            // 创建编译配置，使用框架的 CompileConfig
            // 设置在 question_generator 节点之后中断
            CompileConfig compileConfig = CompileConfig.builder()
                    .saverConfig(saverConfig)
                    .interruptAfter(NODE_QUESTION_GENERATOR)
                    .build();

            // 编译图
            compiledGraph = stateGraph.compile(compileConfig);

            log.info("Interview workflow graph built successfully with Redis checkpoint support");
        } catch (GraphStateException e) {
            log.error("Failed to build workflow graph: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build workflow graph", e);
        }
    }

    /**
     * 将 Function<OverAllState, OverAllState> 适配为 NodeAction
     * NodeAction.apply 返回 Map<String, Object>，而原始节点返回 OverAllState
     */
    private NodeAction adaptNodeAction(
            java.util.function.Function<OverAllState, OverAllState> nodeFunction) {
        return state -> {
            OverAllState result = nodeFunction.apply(state);
            return result.data();
        };
    }

    /**
     * 创建异步决策边缘动作 - 决定下一步走哪个分支
     */
    private AsyncEdgeAction createDeciderAsyncEdgeAction() {
        return state -> {
            DecisionAction action = (DecisionAction) state.value(InterviewWorkflowState.DECISION_ACTION).orElse(DecisionAction.ASK);
            return CompletableFuture.completedFuture(action.name());
        };
    }

    /**
     * 执行工作流到 question_generator 节点（用于 /current 接口）
     * 执行流程：entry → question_generator → [中断]
     *
     * @param sessionId 会话ID
     * @return 中断后的状态
     */
    public OverAllState executeToQuestionGenerator(String sessionId) {
        log.info("Executing workflow to question generator: sessionId={}", sessionId);

        try {
            // 创建初始状态
            Map<String, Object> initialStateData = new HashMap<>();
            initialStateData.put(InterviewWorkflowState.SESSION_ID, sessionId);
            initialStateData.put(InterviewWorkflowState.CURRENT_QUESTION_INDEX, 0);

            // 创建 RunnableConfig，只设置 threadId（不设置 checkPointId，让框架创建新 checkpoint）
            // checkpoint 会在 question_generator 节点中断后自动保存
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            // 执行工作流
            Optional<OverAllState> resultOpt = compiledGraph.invoke(initialStateData, config);

            if (resultOpt.isPresent()) {
                OverAllState state = resultOpt.get();
                log.info("Workflow interrupted at init question: sessionId={}, questionIndex={}",
                        sessionId, state.value(InterviewWorkflowState.CURRENT_QUESTION_INDEX).orElse(0));

                return state;
            } else {
                log.warn("init Workflow returned empty result: sessionId={}", sessionId);
                throw new RuntimeException("工作流执行返回空结果");
            }

        } catch (Exception e) {
            log.error("Workflow execution failed: sessionId={}, error={}", sessionId, e.getMessage(), e);
            interviewStreamService.publishError(sessionId, "工作流执行失败: " + e.getMessage());
            throw new RuntimeException("工作流执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步恢复工作流执行（用于 /answer 接口）
     * 工作流在后台执行，通过 SSE 推送结果
     *
     * @param sessionId 会话ID
     * @param questionIndex 当前问题索引（恢复时传入，用于验证）
     * @param userAnswer 用户答案
     */
    @Async
    public void resumeAsync(String sessionId, Integer questionIndex, String userAnswer) {
        log.info("Starting async workflow resume: sessionId={}, questionIndex={}", sessionId, questionIndex);

        try {
            // 创建 HumanFeedback，包含用户答案
            Map<String, Object> feedbackData = new HashMap<>();
            feedbackData.put(InterviewWorkflowState.CURRENT_ANSWER, userAnswer);
            feedbackData.put(InterviewWorkflowState.CURRENT_QUESTION_INDEX, questionIndex);

            OverAllState.HumanFeedback humanFeedback = new OverAllState.HumanFeedback(feedbackData, NODE_SCORER);

            // 创建 RunnableConfig，只设置 threadId（不设置 checkPointId，让框架自动找到最近的中断点）
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            // 恢复工作流执行
            // 注意：SSE 推送由各个节点（QuestionGeneratorNode、FinalReporterNode）直接调用
            Optional<OverAllState> resultOpt = compiledGraph.resume(humanFeedback, config);

            if (resultOpt.isEmpty()) {
                log.warn("Workflow resume returned empty result: sessionId={}", sessionId);
                interviewStreamService.publishError(sessionId, "工作流恢复执行返回空结果");
            }

        } catch (Exception e) {
            log.error("Async workflow resume failed: sessionId={}, error={}", sessionId, e.getMessage(), e);
            interviewStreamService.publishError(sessionId, "工作流恢复失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前工作流状态（用于中断恢复）
     * 返回 StateSnapshot，需要从中提取 OverAllState
     */
    public Optional<StateSnapshot> getWorkflowState(String sessionId) {
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            return compiledGraph.stateOf(config);
        } catch (NoSuchElementException e) {
            // checkpoint 不存在，返回空
            log.info("Workflow checkpoint not found: sessionId={}", sessionId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get workflow state: sessionId={}, error={}", sessionId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 检查是否有未完成的工作流
     */
    public boolean hasActiveWorkflow(String sessionId) {
        return getWorkflowState(sessionId).isPresent();
    }

}

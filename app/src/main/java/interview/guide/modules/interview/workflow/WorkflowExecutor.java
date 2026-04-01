package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncCommandAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
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

    /**
     * 决策结果常量
     */
    private static final String DECISION_ASK = "ASK";
    private static final String DECISION_SWITCH = "SWITCH";
    private static final String DECISION_FINISH = "FINISH";

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
                           InterviewStreamService interviewStreamService,
                           RedisService redisService) {
        this.entryNode = entryNode;
        this.questionGeneratorNode = questionGeneratorNode;
        this.scorerNode = scorerNode;
        this.deciderNode = deciderNode;
        this.roleSwitcherNode = roleSwitcherNode;
        this.finalReporterNode = finalReporterNode;
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
                // 所有 key 都使用 REPLACE 策略
                strategies.put("*", KeyStrategy.REPLACE);
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

            // 添加普通边 - START -> entry 是隐式入口
            stateGraph.addEdge(StateGraph.START, NODE_ENTRY);
            stateGraph.addEdge(NODE_ENTRY, NODE_QUESTION_GENERATOR);
            stateGraph.addEdge(NODE_QUESTION_GENERATOR, NODE_SCORER);
            stateGraph.addEdge(NODE_SCORER, NODE_DECIDER);
            stateGraph.addEdge(NODE_ROLE_SWITCHER, NODE_QUESTION_GENERATOR);
            stateGraph.addEdge(NODE_FINAL_REPORTER, StateGraph.END);

            // 添加条件边 - decider 根据决策结果决定下一步
            Map<String, String> edgeMapping = new HashMap<>();
            edgeMapping.put(DECISION_ASK, NODE_QUESTION_GENERATOR);
            edgeMapping.put(DECISION_SWITCH, NODE_ROLE_SWITCHER);
            edgeMapping.put(DECISION_FINISH, NODE_FINAL_REPORTER);

            // 使用 AsyncCommandAction.of 将 AsyncEdgeAction 转换为 AsyncCommandAction
            stateGraph.addConditionalEdges(NODE_DECIDER, AsyncCommandAction.of(createDeciderAsyncEdgeAction()), edgeMapping);

            // 创建 Redis Saver
            this.redisSaver = new RedisSaver(redisService.getClient());

            // 创建 SaverConfig
            SaverConfig saverConfig = SaverConfig.builder()
                    .register(WORKFLOW_CHECKPOINT_PREFIX, redisSaver)
                    .build();

            // 创建编译配置，使用框架的 CompileConfig
            // 设置在 question_generator 节点之后中断
            com.alibaba.cloud.ai.graph.CompileConfig compileConfig = com.alibaba.cloud.ai.graph.CompileConfig.builder()
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
    private com.alibaba.cloud.ai.graph.action.NodeAction adaptNodeAction(
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
        return state -> CompletableFuture.completedFuture(
                (String) state.value("decisionAction").orElse(DECISION_ASK).toString()
        );
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
            initialStateData.put("sessionId", sessionId);
            initialStateData.put("currentQuestionIndex", 0);

            // 创建 RunnableConfig，设置 threadId 和 checkPointId 为 sessionId
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .checkPointId(sessionId)
                    .build();

            // 执行工作流
            Optional<OverAllState> resultOpt = compiledGraph.invoke(initialStateData, config);

            if (resultOpt.isPresent()) {
                OverAllState state = resultOpt.get();
                log.info("Workflow interrupted at question_generator: sessionId={}, questionIndex={}",
                        sessionId, state.value("currentQuestionIndex").orElse(0));

                // 推送问题到 SSE
                pushQuestionToSSE(state);

                return state;
            } else {
                log.warn("Workflow returned empty result: sessionId={}", sessionId);
                throw new RuntimeException("工作流执行返回空结果");
            }

        } catch (Exception e) {
            log.error("Workflow execution failed: sessionId={}, error={}", sessionId, e.getMessage(), e);
            interviewStreamService.publishError(sessionId, "工作流执行失败: " + e.getMessage());
            throw new RuntimeException("工作流执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 恢复工作流执行（用于 /answer 接口）
     * 执行流程：从 question_generator 之后继续 → scorer → decider → [ASK] question_generator → [中断]
     *                                                      → [SWITCH] role_switcher → question_generator → [中断]
     *                                                      → [FINISH] final_reporter → [完成]
     *
     * @param sessionId 会话ID
     * @param questionIndex 当前问题索引（恢复时传入，用于验证）
     * @param userAnswer 用户答案
     * @return 恢复执行后的状态（可能在 question_generator 中断，或在 final_reporter 完成）
     */
    public OverAllState resumeWorkflow(String sessionId, Integer questionIndex, String userAnswer) {
        log.info("Resuming workflow: sessionId={}, questionIndex={}", sessionId, questionIndex);

        try {
            // 创建 HumanFeedback，包含用户答案
            Map<String, Object> feedbackData = new HashMap<>();
            feedbackData.put("userAnswer", userAnswer);
            feedbackData.put("questionIndex", questionIndex);

            OverAllState.HumanFeedback humanFeedback = new OverAllState.HumanFeedback(feedbackData, NODE_SCORER);

            // 创建 RunnableConfig，设置 threadId 和 checkPointId 为 sessionId
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .checkPointId(sessionId)
                    .build();

            // 恢复工作流执行
            Optional<OverAllState> resultOpt = compiledGraph.resume(humanFeedback, config);

            if (resultOpt.isPresent()) {
                OverAllState state = resultOpt.get();
                String sessionIdFromState = (String) state.value("sessionId").orElse(sessionId);

                // 检查是否完成
                Boolean isComplete = (Boolean) state.value("isComplete").orElse(false);
                if (Boolean.TRUE.equals(isComplete)) {
                    log.info("Workflow completed: sessionId={}", sessionIdFromState);
                    handleWorkflowResult(state);
                } else {
                    // 工作流在 question_generator 中断
                    log.info("Workflow interrupted at question_generator: sessionId={}, questionIndex={}",
                            sessionIdFromState, state.value("currentQuestionIndex").orElse(0));
                    pushQuestionToSSE(state);
                }

                return state;
            } else {
                log.warn("Workflow resume returned empty result: sessionId={}", sessionId);
                throw new RuntimeException("工作流恢复执行返回空结果");
            }

        } catch (Exception e) {
            log.error("Workflow resume failed: sessionId={}, error={}", sessionId, e.getMessage(), e);
            interviewStreamService.publishError(sessionId, "工作流恢复失败: " + e.getMessage());
            throw new RuntimeException("工作流恢复失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步执行工作流（保留原有方法，用于其他场景）
     */
    @Async
    public void executeAsync(String sessionId, Map<String, Object> initialData) {
        log.info("Starting async workflow execution: sessionId={}", sessionId);

        try {
            // 创建初始状态
            Map<String, Object> initialStateData = new HashMap<>();
            initialStateData.put("sessionId", sessionId);
            initialStateData.put("currentQuestionIndex", 0);

            // 如果有初始数据，则合并
            if (initialData != null && !initialData.isEmpty()) {
                initialStateData.putAll(initialData);
            }

            // 创建 RunnableConfig
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .checkPointId(sessionId)
                    .build();

            // 执行工作流
            Optional<OverAllState> resultOpt = compiledGraph.invoke(initialStateData, config);

            // 处理工作流结果
            if (resultOpt.isPresent()) {
                handleWorkflowResult(resultOpt.get());
            } else {
                log.warn("Workflow returned empty result: sessionId={}", sessionId);
            }

            log.info("Workflow execution completed: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("Workflow execution failed: sessionId={}, error={}", sessionId, e.getMessage(), e);
            interviewStreamService.publishError(sessionId, "工作流执行失败: " + e.getMessage());
        }
    }

    /**
     * 推送问题到 SSE
     */
    private void pushQuestionToSSE(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        if (sessionId == null) return;

        Map<String, Object> questionData = new HashMap<>();
        questionData.put("sessionId", sessionId);
        questionData.put("questionIndex", state.value("currentQuestionIndex").orElse(0));
        questionData.put("question", state.value("currentQuestion").orElse(""));
        questionData.put("category", state.value("currentCategory").orElse(""));
        questionData.put("difficulty", state.value("currentDifficulty").orElse("BASIC"));
        questionData.put("knowledgeBaseName", state.value("knowledgeBaseName").orElse(null));
        questionData.put("createdByPerspectiveId", state.value("createdByPerspectiveId").orElse(null));
        questionData.put("createdByPerspectiveName", state.value("createdByPerspectiveName").orElse(null));

        interviewStreamService.publishQuestion(sessionId, questionData);
    }

    /**
     * 处理工作流执行结果
     */
    private void handleWorkflowResult(OverAllState result) {
        String sessionId = (String) result.value("sessionId").orElse(null);

        // 检查是否有最终报告
        Object finalReportObj = result.value("finalReport").orElse(null);
        if (finalReportObj != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> reportData = (Map<String, Object>) finalReportObj;
            interviewStreamService.publishComplete(sessionId, reportData);
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
                    .checkPointId(sessionId)
                    .build();

            return compiledGraph.stateOf(config);
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

    /**
     * 清理工作流检查点
     */
    public void clearWorkflowCheckpoint(String sessionId) {
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .checkPointId(sessionId)
                    .build();

            redisSaver.clear(config);
            log.info("Cleared workflow checkpoint: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Failed to clear workflow checkpoint: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 构建问题数据，用于 SSE 推送
     */
    public Map<String, Object> buildQuestionData(OverAllState state) {
        return Map.of(
                "sessionId", state.value("sessionId").orElse(""),
                "questionIndex", state.value("currentQuestionIndex").orElse(0),
                "question", state.value("currentQuestion").orElse(""),
                "category", state.value("currentCategory").orElse(""),
                "difficulty", state.value("currentDifficulty").orElse("BASIC")
        );
    }
}

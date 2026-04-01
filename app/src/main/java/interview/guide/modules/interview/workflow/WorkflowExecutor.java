package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncCommandAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import interview.guide.modules.interview.service.InterviewStreamService;
import lombok.RequiredArgsConstructor;
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
 * 负责构建和执行状态图工作流
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowExecutor {

    private final EntryNode entryNode;
    private final QuestionGeneratorNode questionGeneratorNode;
    private final ScorerNode scorerNode;
    private final DeciderNode deciderNode;
    private final RoleSwitcherNode roleSwitcherNode;
    private final FinalReporterNode finalReporterNode;
    private final InterviewStreamService interviewStreamService;

    private CompiledGraph compiledGraph;

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
     * 在 PostConstruct 中构建状态图
     */
    @PostConstruct
    public void buildWorkflowGraph() {
        log.info("Building interview workflow graph...");

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

            // 编译图
            compiledGraph = stateGraph.compile();

            log.info("Interview workflow graph built successfully");
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
     * 异步执行工作流
     */
    @Async
    public void executeAsync(String sessionId, Map<String, Object> initialData) {
        log.info("Starting workflow execution: sessionId={}", sessionId);

        try {
            // 创建初始状态
            Map<String, Object> initialStateData = new HashMap<>();
            initialStateData.put("sessionId", sessionId);
            initialStateData.put("currentQuestionIndex", 0);

            // 如果有初始数据，则合并
            if (initialData != null && !initialData.isEmpty()) {
                initialStateData.putAll(initialData);
            }

            // 执行工作流
            Optional<OverAllState> resultOpt = compiledGraph.invoke(initialStateData);

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
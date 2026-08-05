package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncCommandAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import interview.guide.modules.interview.model.InterviewSessionEntity.WorkflowStatus;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.interview.service.InterviewStreamService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;

/**
 * 面试工作流执行器
 * 支持 checkpoint（检查点）和 resume（恢复）功能
 * <p>
 * 使用 PostgresSaver 持久化 checkpoint，服务重启后可从断点恢复。
 * <p>
 * 工作流执行流程：
 * 1. /current 接口 → entry → question_generator → [中断等待答案]
 * 2. /answer 接口 → updateState(答案) → scorer → decider → [ASK] question_generator → [中断等待答案]
 *                                                  → [SWITCH] role_switcher → question_generator → [中断等待答案]
 *                                                  → [FINISH] final_reporter → [完成]
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
    private final InterviewPersistenceService persistenceService;
    private final DataSource dataSource;

    // PostgresSaver 连接参数（从 application.yml 注入）
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

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
    private static final String NODE_SEARCH_PREPARATOR = "search_preparator";

   public WorkflowExecutor(EntryNode entryNode,
                          QuestionGeneratorNode questionGeneratorNode,
                          ScorerNode scorerNode,
                          DeciderNode deciderNode,
                          RoleSwitcherNode roleSwitcherNode,
                          FinalReporterNode finalReporterNode,
                          SearchPreparatorNode searchPreparatorNode,
                          InterviewStreamService interviewStreamService,
                          InterviewPersistenceService persistenceService,
                          DataSource dataSource) {
        this.entryNode = entryNode;
        this.questionGeneratorNode = questionGeneratorNode;
        this.scorerNode = scorerNode;
        this.deciderNode = deciderNode;
        this.roleSwitcherNode = roleSwitcherNode;
        this.finalReporterNode = finalReporterNode;
        this.searchPreparatorNode = searchPreparatorNode;
        this.interviewStreamService = interviewStreamService;
        this.persistenceService = persistenceService;
        this.dataSource = dataSource;
    }

    /**
     * 在 PostConstruct 中构建状态图
     */
    @PostConstruct
    public void buildWorkflowGraph() {
        log.info("Building interview workflow graph with PostgreSQL checkpoint support...");

        try {
            // 创建 KeyStrategyFactory - 使用 REPLACE 策略覆盖所有值
            KeyStrategyFactory keyStrategyFactory = () -> {
                Map<String, KeyStrategy> strategies = new HashMap<>();
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
                strategies.put(InterviewWorkflowState.QUESTION_DIRECTION, KeyStrategy.REPLACE);
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

            // 添加节点
            stateGraph.addNode(NODE_ENTRY, AsyncNodeAction.node_async(adaptNodeAction(entryNode::execute)));
            stateGraph.addNode(NODE_QUESTION_GENERATOR, AsyncNodeAction.node_async(adaptNodeAction(questionGeneratorNode::execute)));
            stateGraph.addNode(NODE_SCORER, AsyncNodeAction.node_async(adaptNodeAction(scorerNode::execute)));
            stateGraph.addNode(NODE_DECIDER, AsyncNodeAction.node_async(adaptNodeAction(deciderNode::execute)));
            stateGraph.addNode(NODE_ROLE_SWITCHER, AsyncNodeAction.node_async(adaptNodeAction(roleSwitcherNode::execute)));
            stateGraph.addNode(NODE_FINAL_REPORTER, AsyncNodeAction.node_async(adaptNodeAction(finalReporterNode::execute)));
            stateGraph.addNode(NODE_SEARCH_PREPARATOR, AsyncNodeAction.node_async(adaptNodeAction(searchPreparatorNode::execute)));

            // 添加普通边
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

            stateGraph.addConditionalEdges(NODE_DECIDER, AsyncCommandAction.of(createDeciderAsyncEdgeAction()), edgeMapping);

            stateGraph.addEdge(NODE_SEARCH_PREPARATOR, NODE_QUESTION_GENERATOR);

            // 从 datasource URL 解析 PG 连接参数
            String[] pgParams = parsePgUrl(datasourceUrl);

            // 创建 PostgresSaver（自动建表）
            // 动态判断 checkpoint 表是否已存在（PostgresSaver 的 CREATE INDEX 不带 IF NOT EXISTS）
            boolean needCreateTables = !checkpointTablesExist();
            PostgresSaver postgresSaver = PostgresSaver.builder()
                    .host(pgParams[0])
                    .port(Integer.parseInt(pgParams[1]))
                    .user(dbUser)
                    .password(dbPassword)
                    .database(pgParams[2])
                    .createTables(needCreateTables)
                    .build();

            // 创建 SaverConfig
            SaverConfig saverConfig = SaverConfig.builder()
                    .register(postgresSaver)
                    .build();

            // 设置在 question_generator 节点之后中断
            CompileConfig compileConfig = CompileConfig.builder()
                    .saverConfig(saverConfig)
                    .interruptAfter(NODE_QUESTION_GENERATOR)
                    .build();

            // 编译图
            compiledGraph = stateGraph.compile(compileConfig);

            log.info("Interview workflow graph built successfully with PostgreSQL checkpoint support");
        } catch (GraphStateException e) {
            log.error("Failed to build workflow graph: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build workflow graph", e);
        }
    }

    /**
     * 解析 JDBC URL 获取 host、port、database
     * 输入: jdbc:postgresql://localhost:5432/interview_guide
     * 输出: ["localhost", "5432", "interview_guide"]
     */
    private String[] parsePgUrl(String url) {
        // jdbc:postgresql://host:port/database
        String stripped = url.replace("jdbc:postgresql://", "");
        String[] hostPortDb = stripped.split("/");
        String[] hostPort = hostPortDb[0].split(":");
        return new String[]{
                hostPort[0],
                hostPort.length > 1 ? hostPort[1] : "5432",
                hostPortDb.length > 1 ? hostPortDb[1].split("\\?")[0] : "postgres"
        };
    }

    /**
     * 检查 PostgresSaver 的 checkpoint 表是否已存在
     * PostgresSaver 的 CREATE INDEX 不带 IF NOT EXISTS，表已存在时不能再调 createTables
     */
    private boolean checkpointTablesExist() {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.ResultSet rs = conn.getMetaData().getTables(null, null, "graphcheckpoint",
                     new String[]{"TABLE"})) {
            return rs.next();
        } catch (Exception e) {
            log.warn("Failed to check checkpoint table existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将 Function<OverAllState, OverAllState> 适配为 NodeAction
     */
    private NodeAction adaptNodeAction(
            java.util.function.Function<OverAllState, OverAllState> nodeFunction) {
        return state -> {
            OverAllState result = nodeFunction.apply(state);
            return result.data();
        };
    }

    /**
     * 创建异步决策边缘动作
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
     */
   public OverAllState executeToQuestionGenerator(String sessionId) {
        log.info("Executing workflow to question generator: sessionId={}", sessionId);

        try {
            // invoke 前设置 PROCESSING（重启后能被 WorkflowRecoveryRunner 扫到并恢复）
            persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.PROCESSING);

            Map<String, Object> initialStateData = new HashMap<>();
            initialStateData.put(InterviewWorkflowState.SESSION_ID, sessionId);
            initialStateData.put(InterviewWorkflowState.CURRENT_QUESTION_INDEX, 0);

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            Optional<OverAllState> resultOpt = compiledGraph.invoke(initialStateData, config);

            // 中断后设置 workflow_status = AWAITING_ANSWER
            persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.AWAITING_ANSWER);

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
     * 使用 2.0.0-M1.1 新范式：updateState + invoke
     *
     * @param sessionId 会话ID
     * @param questionIndex 当前问题索引
     * @param userAnswer 用户答案
     */
    @Async
    public void resumeAsync(String sessionId, Integer questionIndex, String userAnswer) {
        log.info("Starting async workflow resume: sessionId={}, questionIndex={}", sessionId, questionIndex);

        try {
            // 设置 workflow_status = PROCESSING（标记正在执行，重启后需要恢复）
            persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.PROCESSING);

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            // updateState: 把答案写入 checkpoint，返回带 nextNode 的新 config
            Map<String, Object> values = new HashMap<>();
            values.put(InterviewWorkflowState.CURRENT_ANSWER, userAnswer);
            values.put(InterviewWorkflowState.CURRENT_QUESTION_INDEX, questionIndex);

            RunnableConfig newConfig = compiledGraph.updateState(config, values, NODE_SCORER);

            log.info("Workflow resume: state updated, nextNode from checkpoint, sessionId={}", sessionId);

           // invoke: 从 nextNode（scorer）继续执行
           Optional<OverAllState> resultOpt = compiledGraph.invoke(Map.of(), newConfig);

            if (resultOpt.isEmpty()) {
                log.warn("Workflow resume returned empty result: sessionId={}", sessionId);
                interviewStreamService.publishError(sessionId, "工作流恢复执行返回空结果");
            }

            // invoke 后判断：中断在 question_generator（等待答题）还是走到 END（面试结束）
            RunnableConfig checkConfig = RunnableConfig.builder().threadId(sessionId).build();
            updateStatus(sessionId, checkConfig);

            log.info("Workflow resume completed: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("Async workflow resume failed: sessionId={}, error={}", sessionId, e.getMessage(), e);
            interviewStreamService.publishError(sessionId, "工作流恢复失败: " + e.getMessage());
            // 异常时回滚为 AWAITING_ANSWER，避免用户卡在 PROCESSING 无法继续
            persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.AWAITING_ANSWER);
        }
    }

    /**
     * 恢复中断的工作流（供启动恢复器调用）
     * 用 snapshot.config()（带 checkPointId）触发 initializeFromResume，
     * 引擎从 checkpoint 的 nextNode 继续执行而非从头跑
     */
    @Async
    public void recoverWorkflow(String sessionId) {
        log.info("Recovering interrupted workflow: sessionId={}", sessionId);

        try {
            RunnableConfig threadConfig = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            // 检查是否有 checkpoint
            Optional<StateSnapshot> snapshotOpt = compiledGraph.stateOf(threadConfig);
            if (snapshotOpt.isEmpty()) {
                log.warn("Recovery skipped - no checkpoint found: sessionId={}", sessionId);
                persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.AWAITING_ANSWER);
                return;
            }

            final StateSnapshot snapshot = snapshotOpt.get();
            String nextNode = snapshot.next();
            log.info("Recovering from checkpoint: sessionId={}, node={}, next={}",
                    sessionId, snapshot.node(), nextNode);

           // 如果 nextNode 指向 END（工作流已完成），直接标记终态
            if (com.alibaba.cloud.ai.graph.StateGraph.END.equals(nextNode)) {
                log.info("Recovery: workflow already completed (next=END), marking DONE: sessionId={}", sessionId);
                persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.DONE);
                return;
            }

            // 防 gap：如果 nextNode 是 scorer 但 checkpoint 里没有 CURRENT_ANSWER，
            // 说明崩溃发生在 updateState 写入答案之前，降级为 AWAITING_ANSWER 让用户重试
            if (NODE_SCORER.equals(nextNode)) {
                Object savedAnswer = snapshot.state().value(InterviewWorkflowState.CURRENT_ANSWER).orElse(null);
                if (savedAnswer == null || savedAnswer.toString().isBlank()) {
                    log.warn("Recovery: scorer has no CURRENT_ANSWER in checkpoint, downgrading to AWAITING_ANSWER: sessionId={}", sessionId);
                    persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.AWAITING_ANSWER);
                    return;
                }
            }

            // 用 snapshot.config()（带 checkPointId）触发 initializeFromResume，
            // 这样引擎才会从 checkpoint 的 nextNode 继续，而不是从头跑
            Optional<OverAllState> resultOpt = compiledGraph.invoke(Map.of(), snapshot.config());

            if (resultOpt.isEmpty()) {
                log.warn("Recovery returned empty result: sessionId={}", sessionId);
            }

            // 恢复后判断：如果中断在 question_generator 之后（等待答题），设 AWAITING_ANSWER；
            // 如果走到了 END（面试结束），设 DONE
            updateStatus(sessionId, threadConfig);

            log.info("Workflow recovery completed: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("Workflow recovery failed: sessionId={}, error={}", sessionId, e.getMessage(), e);
            // 恢复失败，回滚为 AWAITING_ANSWER，避免卡死（用户可重新提交答案触发恢复）
            persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.AWAITING_ANSWER);
        }
    }

    private void updateStatus(String sessionId, RunnableConfig threadConfig) {
        Optional<StateSnapshot> postSnapshot = compiledGraph.stateOf(threadConfig);
        if (postSnapshot.isPresent() && StateGraph.END.equals(postSnapshot.get().next())) {
            persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.DONE);
        } else {
            persistenceService.updateWorkflowStatus(sessionId, WorkflowStatus.AWAITING_ANSWER);
        }
    }

}

package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewerRoleEntity;
import interview.guide.modules.interview.repository.InterviewerRoleRepository;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 决策节点 - 由 LLM 驱动决策下一步动作
 * 决策类型: ASK (下一题), SWITCH (角色切换), FINISH (结束)
 */
@Slf4j
@Component
public class DeciderNode {

    private final InterviewPersistenceService persistenceService;
    private final InterviewerRoleRepository interviewerRoleRepository;
    private final ChatClient chatClient;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;

    @Autowired
    public DeciderNode(InterviewPersistenceService persistenceService,
                       InterviewerRoleRepository interviewerRoleRepository,
                       ChatClient.Builder chatClientBuilder,
                       StructuredOutputInvoker structuredOutputInvoker,
                       ObjectMapper objectMapper) {
        this.persistenceService = persistenceService;
        this.interviewerRoleRepository = interviewerRoleRepository;
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.objectMapper = objectMapper;
    }

    // LLM 输出结构（decision 直接用枚举）
    private record DecisionOutput(
            DecisionAction decision,  // ASK, SWITCH, FINISH
            Long nextPerspectiveId,   // 如果是 SWITCH，指定下一个视角ID
            String reason            // 决策原因
    ) {
    }

    private final BeanOutputConverter<DecisionOutput> outputConverter =
            new BeanOutputConverter<>(DecisionOutput.class);

    @Value("classpath:prompts/interview-decider-system.st")
    private Resource systemPromptResource;

    @Value("classpath:prompts/interview-decider-user.st")
    private Resource userPromptResource;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value(InterviewWorkflowState.SESSION_ID).orElse(null);
        int currentIndex = (Integer) state.value(InterviewWorkflowState.CURRENT_QUESTION_INDEX).orElse(0);
        Integer score = (Integer) state.value(InterviewWorkflowState.SCORE).orElse(0);
        String feedback = (String) state.value(InterviewWorkflowState.FEEDBACK).orElse("");
        String adjustedDifficulty = (String) state.value(InterviewWorkflowState.ADJUSTED_DIFFICULTY).orElse("BASIC");
        Long currentPerspectiveId = ((Number) state.value(InterviewWorkflowState.CURRENT_PERSPECTIVE_ID).orElse(0L)).longValue();

        log.info("Decider node: sessionId={}, index={}, score={}, feedback={}, difficulty={}, perspectiveId={}",
                sessionId, currentIndex, score, feedback, adjustedDifficulty, currentPerspectiveId);

        if (sessionId == null) {
            log.error("Decider node: sessionId is null");
            state.updateState(Map.of(InterviewWorkflowState.DECISION_ACTION, DecisionAction.FINISH));
            return state;
        }

        try {
            // 获取会话
            Optional<InterviewSessionEntity> sessionOpt = persistenceService.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.error("Decider node: session not found, sessionId={}", sessionId);
                state.updateState(Map.of(InterviewWorkflowState.DECISION_ACTION, DecisionAction.FINISH));
                return state;
            }
            InterviewSessionEntity session = sessionOpt.get();

            // 检查是否已超出总题数
            Integer totalQuestions = session.getTotalQuestions();
            if (totalQuestions != null && currentIndex >= totalQuestions - 1) {
                log.info("Decider node: last question reached, finishing interview");
                state.updateState(Map.of(
                        InterviewWorkflowState.DECISION_ACTION, DecisionAction.FINISH,
                        InterviewWorkflowState.IS_COMPLETE, true
                ));
                return state;
            }

            // 检查面试是否已完成
            if (session.getStatus() == InterviewSessionEntity.SessionStatus.COMPLETED ||
                    session.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED) {
                log.info("Decider node: interview already completed");
                state.updateState(Map.of(
                        InterviewWorkflowState.DECISION_ACTION, DecisionAction.FINISH,
                        InterviewWorkflowState.IS_COMPLETE, true
                ));
                return state;
            }

            // 获取当前视角信息
            String currentPerspectiveName = "";
            if (currentPerspectiveId > 0) {
                Optional<InterviewerRoleEntity> roleOpt = interviewerRoleRepository.findById(currentPerspectiveId);
                if (roleOpt.isPresent()) {
                    currentPerspectiveName = roleOpt.get().getRoleName();
                }
            }

            // 获取所有视角的历史答题记录（按视角分组）
            Map<Long, List<InterviewAnswerEntity>> answersByPerspective = getAnswersByPerspective(session);

            // 调用 LLM 决策
            DecisionOutput decision = makeDecision(
                    session, currentPerspectiveId, currentPerspectiveName,
                    currentIndex, score, feedback, adjustedDifficulty,
                    answersByPerspective
            );

            // 更新状态
            int nextIndex = currentIndex + 1;
            Map<String, Object> updatedState = new HashMap<>();
            updatedState.put(InterviewWorkflowState.DECISION_ACTION, decision.decision());
            updatedState.put(InterviewWorkflowState.CURRENT_QUESTION_INDEX, nextIndex);
            updatedState.put(InterviewWorkflowState.NEXT_QUESTION_INDEX, nextIndex);
            updatedState.put(InterviewWorkflowState.DECISION_REASON, decision.reason());

            // 如果是 SWITCH，设置下一个视角
            if (decision.decision() == DecisionAction.SWITCH && decision.nextPerspectiveId() != null) {
                updatedState.put(InterviewWorkflowState.NEXT_PERSPECTIVE_ID, decision.nextPerspectiveId());
                updatedState.put(InterviewWorkflowState.CURRENT_PERSPECTIVE_ID, decision.nextPerspectiveId());
            }

            state.updateState(updatedState);

            log.info("Decider node: decision={}, reason={}, nextIndex={}, nextPerspective={}",
                    decision.decision(), decision.reason(), nextIndex, decision.nextPerspectiveId());

        } catch (Exception e) {
            log.error("Decider node error: sessionId={}, error={}", sessionId, e.getMessage(), e);
            state.updateState(Map.of(InterviewWorkflowState.DECISION_ACTION, DecisionAction.FINISH));
        }

        return state;
    }

    /**
     * 获取各视角的答题记录
     */
    private Map<Long, List<InterviewAnswerEntity>> getAnswersByPerspective(
            InterviewSessionEntity session) {
        Map<Long, List<InterviewAnswerEntity>> result = new HashMap<>();

        if (session.getSelectedPerspectives() == null || session.getSelectedPerspectives().isBlank()) {
            return result;
        }

        try {
            List<Long> selectedPerspectives = objectMapper.readValue(
                    session.getSelectedPerspectives(), new TypeReference<List<Long>>() {
                    });

            for (Long perspectiveId : selectedPerspectives) {
                List<InterviewAnswerEntity> answers =
                        persistenceService.findAnswersBySessionAndPerspective(session.getSessionId(), perspectiveId);
                if (!answers.isEmpty()) {
                    result.put(perspectiveId, answers);
                }
            }
        } catch (Exception e) {
            log.error("<UNK> selectedPerspectives <UNK>: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * 调用 LLM 做决策
     */
    private DecisionOutput makeDecision(
            InterviewSessionEntity session,
            Long currentPerspectiveId,
            String currentPerspectiveName,
            int questionIndex,
            int score,
            String feedback,
            String difficulty,
            Map<Long, List<InterviewAnswerEntity>> answersByPerspective) throws IOException {

        // 构建提示词
        String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        String userPromptTemplate = userPromptResource.getContentAsString(StandardCharsets.UTF_8);

        // 构建当前视角的答题历史
        StringBuilder historyBuilder = new StringBuilder();
        List<InterviewAnswerEntity> currentPerspectiveAnswers = answersByPerspective.get(currentPerspectiveId);
        if (currentPerspectiveAnswers != null && !currentPerspectiveAnswers.isEmpty()) {
            historyBuilder.append(String.format("【%s】视角答题记录：\n", currentPerspectiveName));
            for (InterviewAnswerEntity answer : currentPerspectiveAnswers) {
                historyBuilder.append(String.format("Q%d: %s\n得分: %d\n反馈: %s\n\n",
                        answer.getQuestionIndex(),
                        answer.getQuestion(),
                        answer.getScore() != null ? answer.getScore() : 0,
                        answer.getFeedback() != null ? answer.getFeedback() : "无"));
            }
        }

        // 检查其他视角的答题情况
        StringBuilder otherPerspectivesBuilder = new StringBuilder();
        for (Map.Entry<Long, List<InterviewAnswerEntity>> entry : answersByPerspective.entrySet()) {
            if (!entry.getKey().equals(currentPerspectiveId)) {
                Optional<InterviewerRoleEntity> roleOpt = interviewerRoleRepository.findById(entry.getKey());
                String perspectiveName = roleOpt.map(InterviewerRoleEntity::getRoleName).orElse("未知视角");
                otherPerspectivesBuilder.append(String.format("【%s】已回答 %d 题\n", perspectiveName, entry.getValue().size()));
            }
        }

        // 获取可选视角列表（包含权重、已出题数量）
        StringBuilder availablePerspectivesBuilder = new StringBuilder();
        if (session.getSelectedPerspectives() != null && !session.getSelectedPerspectives().isBlank()) {
            try {
                // 解析会话级权重配置
                final Map<Long, Double> sessionWeights;
                if (session.getPerspectiveWeights() != null && !session.getPerspectiveWeights().isBlank()) {
                    sessionWeights = objectMapper.readValue(
                            session.getPerspectiveWeights(), new TypeReference<Map<Long, Double>>() {});
                } else {
                    sessionWeights = null;
                }
                List<Long> selectedPerspectives = objectMapper.readValue(
                        session.getSelectedPerspectives(), new TypeReference<>() {
                        });
                for (Long perspectiveId : selectedPerspectives) {
                    Optional<InterviewerRoleEntity> roleOpt = interviewerRoleRepository.findById(perspectiveId);
                    roleOpt.ifPresent(role -> {
                        int answeredCount = 0;
                        List<InterviewAnswerEntity> answers = answersByPerspective.get(perspectiveId);
                        if (answers != null) {
                            answeredCount = answers.size();
                        }
                        // 优先使用会话级权重，否则使用角色表中的默认权重
                        double weight = sessionWeights != null && sessionWeights.containsKey(perspectiveId)
                                ? sessionWeights.get(perspectiveId)
                                : (role.getWeight() != null ? role.getWeight() : 1.0);
                        availablePerspectivesBuilder.append(String.format("- %s (ID:%d, 权重:%.0f%%, 已出题:%d): %s\n",
                                role.getRoleName(),
                                role.getId(),
                                weight * 100,
                                answeredCount,
                                role.getDescription()));
                    });
                }
                log.info("availablePerspectives: {}", availablePerspectivesBuilder);
            } catch (Exception e) {
                log.warn("解析 selectedPerspectives 失败: {}", e.getMessage());
            }
        }

        // 渲染用户提示词
        Map<String, Object> variables = new HashMap<>();
        variables.put("currentPerspective", currentPerspectiveName);
        variables.put("currentQuestionIndex", questionIndex + 1);
        variables.put("totalQuestions", session.getTotalQuestions() != null ? session.getTotalQuestions() : 10);
        variables.put("score", score);
        variables.put("feedback", feedback != null ? feedback : "无");
        variables.put("difficulty", difficulty);
        variables.put("currentPerspectiveHistory", historyBuilder.toString());
        variables.put("otherPerspectivesSummary", otherPerspectivesBuilder.toString());
        variables.put("availablePerspectives", availablePerspectivesBuilder.toString());

        String userPrompt = userPromptTemplate;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            userPrompt = userPrompt.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }

        // 使用 StructuredOutputInvoker 调用 LLM 结构化输出
        DecisionOutput output = structuredOutputInvoker.invoke(
                chatClient,
                systemPrompt,
                userPrompt,
                outputConverter,
                ErrorCode.AI_SERVICE_ERROR,
                "LLM决策解析失败",
                "DeciderNode",
                log
        );

        log.info("Decider LLM parsed: decision={}, nextPerspective={}, reason={}",
                output.decision(), output.nextPerspectiveId(), output.reason());

        // 验证并返回
        if (output.decision() == null) {
            log.warn("LLM 返回的决策为空，使用默认 ASK");
            return new DecisionOutput(DecisionAction.ASK, null, "默认继续");
        }

        return output;
    }
}

package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 决策节点 - 由 LLM 驱动决策下一步动作
 * 决策类型: ASK (下一题), SWITCH (角色切换), FINISH (结束)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeciderNode {

    private static final String DECISION_ASK = "ASK";
    private static final String DECISION_SWITCH = "SWITCH";
    private static final String DECISION_FINISH = "FINISH";

    private final InterviewPersistenceService persistenceService;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        Integer currentIndex = (Integer) state.value("currentQuestionIndex").orElse(0);
        Integer score = (Integer) state.value("score").orElse(0);
        String adjustedDifficulty = (String) state.value("adjustedDifficulty").orElse(null);

        log.info("Decider node: sessionId={}, index={}, score={}, adjustedDifficulty={}",
                sessionId, currentIndex, score, adjustedDifficulty);

        if (sessionId == null) {
            log.error("Decider node: sessionId is null");
            state.updateState(Map.of("decisionAction", DECISION_FINISH));
            return state;
        }

        try {
            // 获取会话
            Optional<InterviewSessionEntity> sessionOpt = persistenceService.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.error("Decider node: session not found, sessionId={}", sessionId);
                state.updateState(Map.of("decisionAction", DECISION_FINISH));
                return state;
            }
            InterviewSessionEntity session = sessionOpt.get();

            // 检查是否已超出总题数
            Integer totalQuestions = session.getTotalQuestions();
            if (totalQuestions != null && currentIndex >= totalQuestions - 1) {
                // 最后一题，结束面试
                log.info("Decider node: last question reached, finishing interview");
                state.updateState(Map.of(
                        "decisionAction", DECISION_FINISH,
                        "isComplete", true
                ));
                return state;
            }

            // 检查面试是否已完成
            if (session.getStatus() == InterviewSessionEntity.SessionStatus.COMPLETED ||
                session.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED) {
                log.info("Decider node: interview already completed");
                state.updateState(Map.of(
                        "decisionAction", DECISION_FINISH,
                        "isComplete", true
                ));
                return state;
            }

            // 简单决策逻辑：
            // - 如果分数低于60且难度不是BASIC，降低难度继续问
            // - 如果分数高于80，考虑切换视角
            // - 否则继续当前视角问下一题
            String decision = DECISION_ASK;
            if (score > 0 && score < 60 && adjustedDifficulty != null && !adjustedDifficulty.equals("BASIC")) {
                // 答得不好，降低难度继续问
                decision = DECISION_ASK;
            } else if (score >= 90 && session.getSelectedPerspectives() != null && !session.getSelectedPerspectives().isBlank()) {
                // 答得很好，考虑切换视角
                decision = DECISION_SWITCH;
            } else {
                // 继续当前视角问下一题
                decision = DECISION_ASK;
            }

            // 更新问题索引，准备下一题
            int nextIndex = currentIndex + 1;
            state.updateState(Map.of(
                    "decisionAction", decision,
                    "nextQuestionIndex", nextIndex,
                    "currentQuestionIndex", nextIndex
            ));

            log.info("Decider node: decision={}, nextIndex={}", decision, nextIndex);

        } catch (Exception e) {
            log.error("Decider node error: sessionId={}, error={}", sessionId, e.getMessage(), e);
            state.updateState(Map.of("decisionAction", DECISION_FINISH));
        }

        return state;
    }
}

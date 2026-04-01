package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.service.DifficultyAdjustmentService;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.interview.service.SingleAnswerEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 评分节点 - 评估用户答案并更新能力画像
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScorerNode {

    private final SingleAnswerEvaluationService singleAnswerEvaluationService;
    private final DifficultyAdjustmentService difficultyAdjustmentService;
    private final InterviewPersistenceService persistenceService;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        Integer questionIndex = (Integer) state.value("questionIndex").orElse(null);
        String userAnswer = (String) state.value("userAnswer").orElse(null);

        log.info("Scorer node: sessionId={}, questionIndex={}, hasAnswer={}",
                sessionId, questionIndex, userAnswer != null && !userAnswer.isBlank());

        if (sessionId == null || questionIndex == null) {
            log.error("Scorer node: missing required parameters");
            return state;
        }

        if (userAnswer == null || userAnswer.isBlank()) {
            log.warn("Scorer node: userAnswer is empty, skipping evaluation");
            return state;
        }

        try {
            // 获取会话
            Optional<InterviewSessionEntity> sessionOpt = persistenceService.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.error("Scorer node: session not found, sessionId={}", sessionId);
                return state;
            }
            InterviewSessionEntity session = sessionOpt.get();

            // 获取当前问题
            List<InterviewAnswerEntity> allAnswers = persistenceService.findAnswersBySessionId(sessionId);
            Optional<InterviewAnswerEntity> answerOpt = allAnswers.stream()
                    .filter(a -> a.getQuestionIndex().equals(questionIndex))
                    .findFirst();
            if (answerOpt.isEmpty()) {
                log.error("Scorer node: answer not found, sessionId={}, questionIndex={}", sessionId, questionIndex);
                return state;
            }
            InterviewAnswerEntity currentAnswer = answerOpt.get();

            // 获取简历文本
            String resumeText = null;
            if (session.getResume() != null) {
                resumeText = session.getResume().getResumeText();
            }
            if (resumeText == null || resumeText.isBlank()) {
                resumeText = "通用面试，无特定简历内容";
            }

            // 评估答案
            SingleAnswerEvaluationService.EvaluationResult evaluationResult = singleAnswerEvaluationService.evaluateAnswer(
                    currentAnswer.getQuestion(),
                    currentAnswer.getCategory(),
                    currentAnswer.getDifficulty(),
                    userAnswer,
                    resumeText,
                    currentAnswer.getReferenceContext()
            );

            // 调整难度
            String adjustedDifficulty = difficultyAdjustmentService.adjustDifficulty(
                    currentAnswer.getDifficulty(), evaluationResult.score());

            // 保存评估结果
            persistenceService.saveAnswerWithDifficulty(
                    sessionId,
                    questionIndex,
                    currentAnswer.getQuestion(),
                    currentAnswer.getCategory(),
                    userAnswer,
                    adjustedDifficulty,
                    currentAnswer.getKnowledgeBaseId(),
                    currentAnswer.getReferenceContext(),
                    evaluationResult.score(),
                    evaluationResult.feedback(),
                    currentAnswer.getCreatedByPerspectiveId(),
                    currentAnswer.getCreatedByPerspectiveName(),
                    currentAnswer.getIsFollowUp(),
                    currentAnswer.getRelatedIndex(),
                    currentAnswer.getRelatedQuestion(),
                    evaluationResult.referenceAnswer(),
                    evaluationResult.getKeyPointsJson()
            );

            // 更新状态
            Map<String, Object> updatedState = Map.of(
                    "score", evaluationResult.score(),
                    "feedback", evaluationResult.feedback(),
                    "adjustedDifficulty", adjustedDifficulty
            );
            state.updateState(updatedState);

            log.info("评分完成: sessionId={}, questionIndex={}, score={}, feedback={}",
                    sessionId, questionIndex, evaluationResult.score(), evaluationResult.feedback());

        } catch (Exception e) {
            log.error("评分失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }

        return state;
    }
}

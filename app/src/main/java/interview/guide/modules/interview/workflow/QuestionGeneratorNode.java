package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.modules.interview.model.AnswerHistoryDTO;
import interview.guide.modules.interview.model.CurrentQuestionDTO;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewerRoleEntity;
import interview.guide.modules.interview.repository.InterviewerRoleRepository;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.interview.service.QuestionGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 问题生成节点 - 生成面试问题
 * 关键：只获取当前角色的历史答题记录，切换角色后只看新角色的历史
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionGeneratorNode {

    private final QuestionGenerationService questionGenerationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewerRoleRepository interviewerRoleRepository;
    private final ObjectMapper objectMapper;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        Integer questionIndex = (Integer) state.value("currentQuestionIndex").orElse(0);

        // 从状态中获取当前视角
        Long currentPerspectiveId = ((Number) state.value("currentPerspectiveId").orElse(0L)).longValue();

        log.info("Question generator node: sessionId={}, index={}, perspectiveId={}",
                sessionId, questionIndex, currentPerspectiveId);

        if (sessionId == null) {
            log.error("Question generator node: sessionId is null");
            return state;
        }

        try {
            // 获取会话实体
            var sessionOpt = persistenceService.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.error("Question generator node: session not found, sessionId={}", sessionId);
                return state;
            }
            var session = sessionOpt.get();

            // 获取简历文本
            String resumeText = null;
            if (session.getResume() != null) {
                resumeText = session.getResume().getResumeText();
            }
            if (resumeText == null || resumeText.isBlank()) {
                resumeText = "通用面试，无特定简历内容";
            }

            // 确定出题视角
            Long selectedPerspectiveId = currentPerspectiveId;
            String selectedPerspectiveName = null;
            String selectedPerspectivePrompt = null;

            if (session.getSelectedPerspectives() != null && !session.getSelectedPerspectives().isBlank()) {
                try {
                    List<Long> selectedPerspectives = objectMapper.readValue(
                            session.getSelectedPerspectives(), new TypeReference<List<Long>>() {});

                    if (selectedPerspectives != null && !selectedPerspectives.isEmpty()) {
                        // 如果没有指定视角，选择第一个
                        if (selectedPerspectiveId == 0) {
                            selectedPerspectiveId = selectedPerspectives.get(0);
                        }

                        InterviewerRoleEntity role = interviewerRoleRepository.findById(selectedPerspectiveId).orElse(null);
                        if (role != null) {
                            selectedPerspectiveName = role.getRoleName();
                            selectedPerspectivePrompt = role.getQuestionPrompt();
                        }

                        // 更新会话的上一题视角ID
                        session.setLastQuestionPerspectiveId(selectedPerspectiveId);
                        persistenceService.updateLastQuestionPerspectiveId(sessionId, selectedPerspectiveId);

                        // 获取该视角下的最新难度
                        Optional<InterviewAnswerEntity> lastAnswerForPerspective =
                                persistenceService.findLastAnswerBySessionAndPerspective(session.getId(), selectedPerspectiveId);
                        if (lastAnswerForPerspective.isPresent()) {
                            session.setCurrentDifficulty(lastAnswerForPerspective.get().getDifficulty());
                        } else {
                            session.setCurrentDifficulty("BASIC");
                        }
                        log.info("问题生成选择视角: sessionId={}, perspectiveId={}, perspectiveName={}, difficulty={}",
                                sessionId, selectedPerspectiveId, selectedPerspectiveName, session.getCurrentDifficulty());
                    }
                } catch (Exception e) {
                    log.warn("解析selectedPerspectives失败: {}", e.getMessage());
                }
            }

            // 关键：只获取当前角色的历史答题记录，而不是所有历史
            List<InterviewAnswerEntity> perspectiveAnswers = List.of();
            if (selectedPerspectiveId != null && selectedPerspectiveId > 0) {
                perspectiveAnswers = persistenceService.findAnswersBySessionAndPerspective(sessionId, selectedPerspectiveId);
                log.info("获取当前角色历史答题记录: sessionId={}, perspectiveId={}, count={}",
                        sessionId, selectedPerspectiveId, perspectiveAnswers.size());
            }

            // 构建历史答题记录（只包含当前角色的）
            List<AnswerHistoryDTO> history = perspectiveAnswers.stream()
                    .map(a -> new AnswerHistoryDTO(
                            a.getQuestionIndex(),
                            a.getQuestion(),
                            a.getCategory(),
                            a.getDifficulty(),
                            a.getUserAnswer(),
                            a.getScore(),
                            a.getFeedback(),
                            a.getCreatedByPerspectiveId(),
                            a.getCreatedByPerspectiveName(),
                            a.getIsFollowUp(),
                            a.getRelatedIndex(),
                            a.getRelatedQuestion()))
                    .toList();

            // 生成问题
            CurrentQuestionDTO questionDTO = questionGenerationService.generateSingleQuestion(
                    session, questionIndex, resumeText, history,
                    selectedPerspectiveId, selectedPerspectivePrompt, selectedPerspectiveName);

            // 保存生成的问题到数据库
            Integer globalRelatedIndex = null;
            if (Boolean.TRUE.equals(questionDTO.isFollowUp()) && questionDTO.relatedIndex() != null) {
                globalRelatedIndex = questionDTO.relatedIndex();
            }
            persistenceService.saveAnswerWithDifficulty(
                    sessionId,
                    questionIndex,
                    questionDTO.question(),
                    questionDTO.category(),
                    null, // userAnswer为null，等待用户回答后更新
                    questionDTO.difficulty(),
                    questionDTO.knowledgeBaseId(),
                    questionDTO.referenceContext(),
                    0,  // 初始评分为0
                    null, // 初始反馈为null
                    selectedPerspectiveId,
                    selectedPerspectiveName,
                    questionDTO.isFollowUp(),
                    globalRelatedIndex,
                    questionDTO.relatedQuestion(),
                    null,
                    null
            );

            // 更新会话状态
            if (session.getStatus() == interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus.CREATED) {
                session.setStatus(interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus.IN_PROGRESS);
                persistenceService.updateSessionStatus(sessionId, interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            }

            // 更新会话的当前问题索引
            persistenceService.updateCurrentQuestionIndex(sessionId, questionIndex);

            // 更新已生成问题数量
            int generated = session.getQuestionsGenerated() != null ? session.getQuestionsGenerated() : 0;
            persistenceService.updateQuestionsGenerated(sessionId, generated + 1);

            // 更新状态，以便 SSE 推送
            Map<String, Object> updatedState = Map.of(
                    "currentQuestionIndex", questionIndex,
                    "currentQuestion", questionDTO.question(),
                    "currentCategory", questionDTO.category(),
                    "currentDifficulty", questionDTO.difficulty() != null ? questionDTO.difficulty() : "BASIC",
                    "knowledgeBaseId", questionDTO.knowledgeBaseId() != null ? questionDTO.knowledgeBaseId() : 0L,
                    "knowledgeBaseName", questionDTO.knowledgeBaseName() != null ? questionDTO.knowledgeBaseName() : "",
                    "createdByPerspectiveId", selectedPerspectiveId != null ? selectedPerspectiveId : 0L,
                    "createdByPerspectiveName", selectedPerspectiveName != null ? selectedPerspectiveName : "",
                    "currentPerspectiveId", selectedPerspectiveId != null ? selectedPerspectiveId : 0L
            );
            state.updateState(updatedState);

            log.info("问题生成完成: sessionId={}, index={}, category={}, difficulty={}, perspective={}",
                    sessionId, questionIndex, questionDTO.category(), questionDTO.difficulty(), selectedPerspectiveName);

        } catch (Exception e) {
            log.error("问题生成失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }

        return state;
    }
}

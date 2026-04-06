package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.modules.interview.model.AnswerHistoryDTO;
import interview.guide.modules.interview.model.CurrentQuestionDTO;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewerRoleEntity;
import interview.guide.modules.interview.repository.InterviewerRoleRepository;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.interview.service.InterviewStreamService;
import interview.guide.modules.interview.service.QuestionGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
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
    private final InterviewStreamService interviewStreamService;
    private final ObjectMapper objectMapper;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value(InterviewWorkflowState.SESSION_ID).orElse(null);
        Integer questionIndex = (Integer) state.value(InterviewWorkflowState.CURRENT_QUESTION_INDEX).orElse(0);

        // 从状态中获取当前视角
        Long currentPerspectiveId = ((Number) state.value(InterviewWorkflowState.CURRENT_PERSPECTIVE_ID).orElse(0L)).longValue();

        log.info("Question generator node: sessionId={}, index={}, perspectiveId={}",
                sessionId, questionIndex, currentPerspectiveId);

        if (sessionId == null) {
            log.error("Question generator node: sessionId is null");
            return state;
        }

        try {
            // 获取会话实体（同时加载简历，避免懒加载问题）
            var sessionOpt = persistenceService.findBySessionIdWithResume(sessionId);
            if (sessionOpt.isEmpty()) {
                log.error("Question generator node: session not found, sessionId={}", sessionId);
                return state;
            }
            var session = sessionOpt.get();

            // 获取 MCP 搜索结果作为补充上下文
            String mcpSearchResult = (String) state.value(InterviewWorkflowState.SEARCH_RESULT).orElse(null);
            Boolean searchEnabled = (Boolean) state.value(InterviewWorkflowState.SEARCH_ENABLED).orElse(false);

            if (searchEnabled && mcpSearchResult != null && !mcpSearchResult.isBlank()) {
                log.info("Using MCP search result: sessionId={}, resultLength={}",
                        sessionId, mcpSearchResult.length());
            }

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

            log.info("开始确定出题视角: sessionId={}, currentPerspectiveId={}, selectedPerspectives字符串={}",
                    sessionId, currentPerspectiveId, session.getSelectedPerspectives());

            if (session.getSelectedPerspectives() != null && !session.getSelectedPerspectives().isBlank()) {
                try {
                    List<Long> selectedPerspectives = objectMapper.readValue(
                            session.getSelectedPerspectives(), new TypeReference<>() {
                            });

                    log.info("解析selectedPerspectives成功: {}", selectedPerspectives);

                    if (selectedPerspectives != null && !selectedPerspectives.isEmpty()) {
                        // 如果没有指定视角，选择第一个
                        if (selectedPerspectiveId == 0) {
                            selectedPerspectiveId = selectedPerspectives.get(0);
                        }

                        log.info("即将查询视角: selectedPerspectiveId={}", selectedPerspectiveId);
                        InterviewerRoleEntity role = interviewerRoleRepository.findById(selectedPerspectiveId).orElse(null);
                        log.info("视角查询结果: role={}", role);
                        if (role != null) {
                            selectedPerspectiveName = role.getRoleName();
                            selectedPerspectivePrompt = role.getQuestionPrompt();
                        } else {
                            log.warn("视角未找到: perspectiveId={}", selectedPerspectiveId);
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
                    log.warn("解析selectedPerspectives失败: {}", e.getMessage(), e);
                }
            } else {
                log.warn("session.getSelectedPerspectives() 为空: sessionId={}", sessionId);
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
                    selectedPerspectiveId, selectedPerspectivePrompt, selectedPerspectiveName,
                    mcpSearchResult);

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

            // 更新状态，以便 SSE 推送和 checkpoint 恢复
            Map<String, Object> updatedState = new HashMap<>();
            updatedState.put(InterviewWorkflowState.CURRENT_QUESTION_INDEX, questionIndex);
            updatedState.put(InterviewWorkflowState.CURRENT_QUESTION, questionDTO.question());
            updatedState.put(InterviewWorkflowState.CURRENT_CATEGORY, questionDTO.category());
            updatedState.put(InterviewWorkflowState.CURRENT_DIFFICULTY, questionDTO.difficulty() != null ? questionDTO.difficulty() : "BASIC");
            updatedState.put(InterviewWorkflowState.KNOWLEDGE_BASE_ID, questionDTO.knowledgeBaseId() != null ? questionDTO.knowledgeBaseId() : 0L);
            updatedState.put(InterviewWorkflowState.KNOWLEDGE_BASE_NAME, questionDTO.knowledgeBaseName() != null ? questionDTO.knowledgeBaseName() : "");
            updatedState.put(InterviewWorkflowState.CREATED_BY_PERSPECTIVE_ID, selectedPerspectiveId != null ? selectedPerspectiveId : 0L);
            updatedState.put(InterviewWorkflowState.CREATED_BY_PERSPECTIVE_NAME, selectedPerspectiveName != null ? selectedPerspectiveName : "");
            updatedState.put(InterviewWorkflowState.CURRENT_PERSPECTIVE_ID, selectedPerspectiveId != null ? selectedPerspectiveId : 0L);
            // 清空搜索结果，避免下一轮继续使用
            updatedState.put(InterviewWorkflowState.SEARCH_RESULT, "");
            updatedState.put(InterviewWorkflowState.SEARCH_ENABLED, false);
            // 追问相关字段也需要放入状态，以便 checkpoint 恢复后 SSE 推送
            updatedState.put(InterviewWorkflowState.IS_FOLLOW_UP, questionDTO.isFollowUp() != null ? questionDTO.isFollowUp() : false);
            updatedState.put(InterviewWorkflowState.RELATED_INDEX, questionDTO.relatedIndex());
            updatedState.put(InterviewWorkflowState.RELATED_QUESTION, questionDTO.relatedQuestion());
            state.updateState(updatedState);

            log.info("问题生成完成: sessionId={}, index={}, category={}, difficulty={}, perspective={}, isFollowUp={}, relatedIndex={}",
                    sessionId, questionIndex, questionDTO.category(), questionDTO.difficulty(), selectedPerspectiveName,
                    questionDTO.isFollowUp(), questionDTO.relatedIndex());

            // 直接推送问题到 SSE，不再依赖 WorkflowExecutor
            Map<String, Object> questionData = new HashMap<>();
            questionData.put("sessionId", sessionId);
            questionData.put("questionIndex", questionIndex);
            questionData.put("question", questionDTO.question());
            questionData.put("category", questionDTO.category());
            questionData.put("difficulty", questionDTO.difficulty() != null ? questionDTO.difficulty() : "BASIC");
            questionData.put("knowledgeBaseId", questionDTO.knowledgeBaseId() != null ? questionDTO.knowledgeBaseId() : 0L);
            questionData.put("knowledgeBaseName", questionDTO.knowledgeBaseName() != null ? questionDTO.knowledgeBaseName() : "");
            questionData.put("createdByPerspectiveId", selectedPerspectiveId != null ? selectedPerspectiveId : 0L);
            questionData.put("createdByPerspectiveName", selectedPerspectiveName != null ? selectedPerspectiveName : "");
            questionData.put("isFollowUp", questionDTO.isFollowUp() != null ? questionDTO.isFollowUp() : false);
            questionData.put("relatedIndex", questionDTO.relatedIndex());
            questionData.put("relatedQuestion", questionDTO.relatedQuestion());
            interviewStreamService.publishQuestion(sessionId, questionData);

        } catch (Exception e) {
            log.error("问题生成失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }

        return state;
    }
}

package interview.guide.modules.interview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 面试持久化服务
 * 面试会话和答案的持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPersistenceService {
    
    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * 保存新的面试会话（无简历模式）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionEntity saveSessionWithoutResume(Long userId, String sessionId,
                                              int totalQuestions,
                                              List<InterviewQuestionDTO> questions) {
        try {
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId(sessionId);
            session.setTotalQuestions(totalQuestions);
            session.setCurrentQuestionIndex(0);
            session.setStatus(InterviewSessionEntity.SessionStatus.CREATED);
            session.setQuestionsJson(objectMapper.writeValueAsString(questions));
            // 初始化自适应难度字段
            session.setCurrentDifficulty("BASIC");
            session.setCategoryScores("{}");
            session.setQuestionsGenerated(0);

            InterviewSessionEntity saved = sessionRepository.save(session);
            log.info("面试会话已保存（无简历模式）: userId={}, sessionId={}", userId, sessionId);

            return saved;
        } catch (JacksonException e) {
            log.error("序列化问题列表失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存会话失败");
        }
    }

    /**
     * 保存新的面试会话（简历模式）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionEntity saveSession(Long userId, String sessionId, Long resumeId,
                                              int totalQuestions,
                                              List<InterviewQuestionDTO> questions) {
        try {
            // 如果没有简历ID，使用无简历模式
            if (resumeId == null) {
                return saveSessionWithoutResume(userId, sessionId, totalQuestions, questions);
            }

            Optional<ResumeEntity> resumeOpt = resumeRepository.findById(resumeId);
            if (resumeOpt.isEmpty()) {
                throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
            }

            ResumeEntity resume = resumeOpt.get();
            // 验证简历属于当前用户
            if (!userId.equals(resume.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用该简历");
            }

            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId(sessionId);
            session.setResume(resume);
            session.setTotalQuestions(totalQuestions);
            session.setCurrentQuestionIndex(0);
            session.setStatus(InterviewSessionEntity.SessionStatus.CREATED);
            session.setQuestionsJson(objectMapper.writeValueAsString(questions));
            // 初始化自适应难度字段
            session.setCurrentDifficulty("BASIC");
            session.setCategoryScores("{}");
            session.setQuestionsGenerated(0);

            InterviewSessionEntity saved = sessionRepository.save(session);
            log.info("面试会话已保存: userId={}, sessionId={}, resumeId={}", userId, sessionId, resumeId);

            return saved;
        } catch (JacksonException e) {
            log.error("序列化问题列表失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存会话失败");
        }
    }

    /**
     * 保存自适应面试会话（不生成问题，问题在 getCurrentQuestionForAdaptive 中实时生成）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionEntity saveAdaptiveSession(Long userId, String sessionId, Long resumeId,
                                                     int totalQuestions, String knowledgeBaseIdsJson) {
        return saveAdaptiveSession(userId, sessionId, resumeId, totalQuestions, knowledgeBaseIdsJson, null, null);
    }

    /**
     * 保存自适应面试会话（支持多视角）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionEntity saveAdaptiveSession(Long userId, String sessionId, Long resumeId,
                                                     int totalQuestions, String knowledgeBaseIdsJson,
                                                     String selectedPerspectivesJson) {
        return saveAdaptiveSession(userId, sessionId, resumeId, totalQuestions, knowledgeBaseIdsJson,
                selectedPerspectivesJson, null);
    }

    /**
     * 保存自适应面试会话（支持多视角 + 会话级权重配置）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewSessionEntity saveAdaptiveSession(Long userId, String sessionId, Long resumeId,
                                                     int totalQuestions, String knowledgeBaseIdsJson,
                                                     String selectedPerspectivesJson,
                                                     String perspectiveWeightsJson) {
        try {
            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setSessionId(sessionId);
            session.setTotalQuestions(totalQuestions);
            session.setCurrentQuestionIndex(0);
            session.setStatus(InterviewSessionEntity.SessionStatus.CREATED);
            session.setCurrentDifficulty("BASIC");
            session.setCategoryScores("{}");
            session.setQuestionsGenerated(0);
            session.setKnowledgeBaseIds(knowledgeBaseIdsJson);
            session.setSelectedPerspectives(selectedPerspectivesJson);
            session.setPerspectiveWeights(perspectiveWeightsJson);

            // 如果有简历ID，关联简历
            if (resumeId != null) {
                Optional<ResumeEntity> resumeOpt = resumeRepository.findById(resumeId);
                if (resumeOpt.isPresent()) {
                    ResumeEntity resume = resumeOpt.get();
                    if (!userId.equals(resume.getUserId())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用该简历");
                    }
                    session.setResume(resume);
                }
            }

            InterviewSessionEntity saved = sessionRepository.save(session);
            log.info("自适应面试会话已保存: userId={}, sessionId={}, totalQuestions={}, hasPerspectives={}, hasWeights={}",
                    userId, sessionId, totalQuestions, selectedPerspectivesJson != null, perspectiveWeightsJson != null);
            return saved;
        } catch (Exception e) {
            log.error("保存自适应会话失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存会话失败");
        }
    }

    /**
     * 更新会话状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionStatus(String sessionId, InterviewSessionEntity.SessionStatus status) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setStatus(status);
            if (status == InterviewSessionEntity.SessionStatus.COMPLETED ||
                status == InterviewSessionEntity.SessionStatus.EVALUATED) {
                session.setCompletedAt(LocalDateTime.now());
            }
            sessionRepository.save(session);
        }
    }

    /**
     * 更新评估状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setEvaluateStatus(status);
            if (error != null) {
                session.setEvaluateError(error.length() > 500 ? error.substring(0, 500) : error);
            } else {
                session.setEvaluateError(null);
            }
            sessionRepository.save(session);
            log.debug("评估状态已更新: sessionId={}, status={}", sessionId, status);
        }
    }
    
    /**
     * 更新当前问题索引
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentQuestionIndex(String sessionId, int index) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setCurrentQuestionIndex(index);
            session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            sessionRepository.save(session);
        }
    }

    /**
     * 更新会话的问题列表（切换知识库时使用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestions(String sessionId, List<InterviewQuestionDTO> questions) {
        try {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isPresent()) {
                InterviewSessionEntity session = sessionOpt.get();
                session.setQuestionsJson(objectMapper.writeValueAsString(questions));
                sessionRepository.save(session);
                log.info("会话问题列表已更新: sessionId={}, questionCount={}", sessionId, questions.size());
            }
        } catch (JacksonException e) {
            log.error("序列化问题列表失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "更新问题列表失败");
        }
    }

    /**
     * 保存面试答案
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveAnswer(String sessionId, int questionIndex,
                                            String question, String category,
                                            String userAnswer, int score, String feedback) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        InterviewAnswerEntity answer = answerRepository
            .findBySession_SessionIdAndQuestionIndex(sessionId, questionIndex)
            .orElseGet(() -> {
                InterviewAnswerEntity created = new InterviewAnswerEntity();
                created.setSession(sessionOpt.get());
                created.setQuestionIndex(questionIndex);
                return created;
            });

        answer.setQuestion(question);
        answer.setCategory(category);
        answer.setUserAnswer(userAnswer);
        answer.setScore(score);
        answer.setFeedback(feedback);

        InterviewAnswerEntity saved = answerRepository.save(answer);
        log.info("面试答案已保存: sessionId={}, questionIndex={}, score={}", 
                sessionId, questionIndex, score);
        
        return saved;
    }
    
    /**
     * 保存面试报告
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveReport(String sessionId, InterviewReportDTO report) {
        try {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("会话不存在: {}", sessionId);
                return;
            }

            InterviewSessionEntity session = sessionOpt.get();
            session.setOverallScore(report.overallScore());
            session.setOverallFeedback(report.overallFeedback());
            session.setStrengthsJson(objectMapper.writeValueAsString(report.strengths()));
            session.setImprovementsJson(objectMapper.writeValueAsString(report.improvements()));
            session.setReferenceAnswersJson(objectMapper.writeValueAsString(report.referenceAnswers()));
            session.setStatus(InterviewSessionEntity.SessionStatus.EVALUATED);
            session.setCompletedAt(LocalDateTime.now());

            sessionRepository.save(session);

            // 查询已存在的答案，建立索引
            List<InterviewAnswerEntity> existingAnswers = answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId);
            java.util.Map<Integer, InterviewAnswerEntity> answerMap = existingAnswers.stream()
                .collect(java.util.stream.Collectors.toMap(
                    InterviewAnswerEntity::getQuestionIndex,
                    a -> a,
                    (a1, a2) -> a1
                ));

            // 建立参考答案索引
            java.util.Map<Integer, InterviewReportDTO.ReferenceAnswer> refAnswerMap = report.referenceAnswers().stream()
                .collect(java.util.stream.Collectors.toMap(
                    InterviewReportDTO.ReferenceAnswer::questionIndex,
                    r -> r,
                    (r1, r2) -> r1
                ));

            List<InterviewAnswerEntity> answersToSave = new java.util.ArrayList<>();

            // 遍历所有评估结果，更新或创建答案记录
            for (InterviewReportDTO.QuestionEvaluation eval : report.questionDetails()) {
                InterviewAnswerEntity answer = answerMap.get(eval.questionIndex());

                if (answer == null) {
                    // 未回答的题目，创建新记录
                    answer = new InterviewAnswerEntity();
                    answer.setSession(session);
                    answer.setQuestionIndex(eval.questionIndex());
                    answer.setQuestion(eval.question());
                    answer.setCategory(eval.category());
                    answer.setUserAnswer(null);  // 未回答
                    log.debug("为未回答的题目 {} 创建答案记录", eval.questionIndex());
                }

                // 更新评分和反馈
                answer.setScore(eval.score());
                answer.setFeedback(eval.feedback());

                // 设置参考答案和关键点
                InterviewReportDTO.ReferenceAnswer refAns = refAnswerMap.get(eval.questionIndex());
                if (refAns != null) {
                    answer.setReferenceAnswer(refAns.referenceAnswer());
                    if (refAns.keyPoints() != null && !refAns.keyPoints().isEmpty()) {
                        answer.setKeyPointsJson(objectMapper.writeValueAsString(refAns.keyPoints()));
                    }
                }

                answersToSave.add(answer);
            }

            answerRepository.saveAll(answersToSave);
            log.info("面试报告已保存: sessionId={}, score={}, 答案数={}",
                sessionId, report.overallScore(), answersToSave.size());

        } catch (JacksonException e) {
            log.error("序列化报告失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 根据会话ID获取会话
     */
    public Optional<InterviewSessionEntity> findBySessionId(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }
    
    /**
     * 获取简历的所有面试记录
     */
    public List<InterviewSessionEntity> findByResumeId(Long resumeId) {
        return sessionRepository.findByResumeIdOrderByCreatedAtDesc(resumeId);
    }

    /**
     * 获取用户的所有面试会话
     */
    public List<InterviewSessionEntity> findAllByUserId(Long userId) {
        return sessionRepository.findAllByUserId(userId);
    }
    
    /**
     * 删除简历的所有面试会话
     * 由于InterviewSessionEntity设置了cascade = CascadeType.ALL, orphanRemoval = true
     * 删除会话会自动删除关联的答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionsByResumeId(Long resumeId) {
        List<InterviewSessionEntity> sessions = sessionRepository.findByResumeIdOrderByCreatedAtDesc(resumeId);
        if (!sessions.isEmpty()) {
            sessionRepository.deleteAll(sessions);
            log.info("已删除 {} 个面试会话（包含所有答案）", sessions.size());
        }
    }
    
    /**
     * 删除单个面试会话
     * 由于InterviewSessionEntity设置了cascade = CascadeType.ALL, orphanRemoval = true
     * 删除会话会自动删除关联的答案
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionBySessionId(String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            sessionRepository.delete(sessionOpt.get());
            log.info("已删除面试会话: sessionId={}", sessionId);
        } else {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
    }
    
    /**
     * 查找未完成的面试会话（CREATED或IN_PROGRESS状态）
     */
    public Optional<InterviewSessionEntity> findUnfinishedSession(Long resumeId) {
        List<InterviewSessionEntity.SessionStatus> unfinishedStatuses = List.of(
            InterviewSessionEntity.SessionStatus.CREATED,
            InterviewSessionEntity.SessionStatus.IN_PROGRESS
        );
        return sessionRepository.findFirstByResumeIdAndStatusInOrderByCreatedAtDesc(resumeId, unfinishedStatuses);
    }
    
    /**
     * 根据会话ID查找所有答案
     */
    public List<InterviewAnswerEntity> findAnswersBySessionId(String sessionId) {
        return answerRepository.findBySession_SessionIdOrderByQuestionIndex(sessionId);
    }

    /**
     * 获取简历的历史提问列表（限制最近的 N 条）
     * 自适应面试：从答案中提取历史问题
     */
    public List<String> getHistoricalQuestionsByResumeId(Long resumeId) {
        // 只查询最近的 10 个会话，避免加载过多历史数据
        List<InterviewSessionEntity> sessions = sessionRepository.findTop10ByResumeIdOrderByCreatedAtDesc(resumeId);

        return sessions.stream()
            .flatMap(session -> {
                List<InterviewAnswerEntity> answers = findAnswersBySessionId(session.getSessionId());
                return answers.stream()
                    .map(InterviewAnswerEntity::getQuestion);
            })
            .filter(q -> q != null && !q.isBlank())
            .distinct()
            .limit(30) // 只保留最近的 30 道题
            .toList();
    }

    /**
     * 更新会话的当前难度
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCurrentDifficulty(String sessionId, String difficulty) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setCurrentDifficulty(difficulty);
            sessionRepository.save(session);
            log.debug("会话难度已更新: sessionId={}, difficulty={}", sessionId, difficulty);
        }
    }

    /**
     * 更新会话的分类得分
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCategoryScores(String sessionId, String categoryScoresJson) {
        try {
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
            if (sessionOpt.isPresent()) {
                InterviewSessionEntity session = sessionOpt.get();
                session.setCategoryScores(categoryScoresJson);
                sessionRepository.save(session);
                log.debug("会话分类得分已更新: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.error("序列化分类得分失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 更新会话的已生成问题数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestionsGenerated(String sessionId, int count) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setQuestionsGenerated(count);
            sessionRepository.save(session);
        }
    }

    /**
     * 更新会话的知识库ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBaseIds(String sessionId, List<Long> knowledgeBaseIds) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            try {
                session.setKnowledgeBaseIds(objectMapper.writeValueAsString(knowledgeBaseIds));
                sessionRepository.save(session);
                log.info("会话知识库ID已更新: sessionId={}, kbIds={}", sessionId, knowledgeBaseIds);
            } catch (JacksonException e) {
                log.error("序列化知识库ID列表失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 保存答案（包含难度和知识库信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveAnswerWithDifficulty(String sessionId, int questionIndex,
                                                        String question, String category,
                                                        String userAnswer, String difficulty,
                                                        Long knowledgeBaseId, String referenceContext,
                                                        int score, String feedback) {
        return saveAnswerWithDifficulty(sessionId, questionIndex, question, category,
                userAnswer, difficulty, knowledgeBaseId, referenceContext, score, feedback,
                null, null, null, null, null, null, null);
    }

    /**
     * 保存答案（包含难度、知识库信息和视角信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveAnswerWithDifficulty(String sessionId, int questionIndex,
                                                        String question, String category,
                                                        String userAnswer, String difficulty,
                                                        Long knowledgeBaseId, String referenceContext,
                                                        int score, String feedback,
                                                        Long createdByPerspectiveId,
                                                        String createdByPerspectiveName) {
        return saveAnswerWithDifficulty(sessionId, questionIndex, question, category,
                userAnswer, difficulty, knowledgeBaseId, referenceContext, score, feedback,
                createdByPerspectiveId, createdByPerspectiveName, null, null, null, null, null);
    }

    /**
     * 保存答案（包含难度、知识库信息、视角信息和追问信息）
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewAnswerEntity saveAnswerWithDifficulty(String sessionId, int questionIndex,
                                                        String question, String category,
                                                        String userAnswer, String difficulty,
                                                        Long knowledgeBaseId, String referenceContext,
                                                        int score, String feedback,
                                                        Long createdByPerspectiveId,
                                                        String createdByPerspectiveName,
                                                        Boolean isFollowUp,
                                                        Integer relatedIndex,
                                                        String relatedQuestion,
                                                        String referenceAnswer,
                                                        String keyPointsJson) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        InterviewAnswerEntity answer = answerRepository
            .findBySession_SessionIdAndQuestionIndex(sessionId, questionIndex)
            .orElseGet(() -> {
                InterviewAnswerEntity created = new InterviewAnswerEntity();
                created.setSession(sessionOpt.get());
                created.setQuestionIndex(questionIndex);
                return created;
            });

        answer.setQuestion(question);
        answer.setCategory(category);
        answer.setUserAnswer(userAnswer);
        answer.setDifficulty(difficulty);
        answer.setKnowledgeBaseId(knowledgeBaseId);
        answer.setReferenceContext(referenceContext);
        answer.setScore(score);
        answer.setFeedback(feedback);
        if (createdByPerspectiveId != null) {
            answer.setCreatedByPerspectiveId(createdByPerspectiveId);
        }
        if (createdByPerspectiveName != null) {
            answer.setCreatedByPerspectiveName(createdByPerspectiveName);
        }
        if (isFollowUp != null) {
            answer.setIsFollowUp(isFollowUp);
        }
        if (relatedIndex != null) {
            answer.setRelatedIndex(relatedIndex);
        }
        if (relatedQuestion != null) {
            answer.setRelatedQuestion(relatedQuestion);
        }
        if (referenceAnswer != null) {
            answer.setReferenceAnswer(referenceAnswer);
        }
        if (keyPointsJson != null) {
            answer.setKeyPointsJson(keyPointsJson);
        }

        InterviewAnswerEntity saved = answerRepository.save(answer);
        log.info("面试答案（含难度+视角+追问+参考答案）已保存: sessionId={}, questionIndex={}, difficulty={}, score={}, perspective={}, isFollowUp={}",
                sessionId, questionIndex, difficulty, score, createdByPerspectiveName, isFollowUp);

        return saved;
    }

    /**
     * 获取所有已评分的答案
     */
    public List<InterviewAnswerEntity> findScoredAnswersBySessionId(String sessionId) {
        return answerRepository.findBySession_SessionIdAndScoreIsNotNullOrderByQuestionIndex(sessionId);
    }

    /**
     * 更新会话的上一题视角ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLastQuestionPerspectiveId(String sessionId, Long perspectiveId) {
        Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isPresent()) {
            InterviewSessionEntity session = sessionOpt.get();
            session.setLastQuestionPerspectiveId(perspectiveId);
            sessionRepository.save(session);
            log.debug("会话视角已更新: sessionId={}, lastQuestionPerspectiveId={}", sessionId, perspectiveId);
        }
    }

    /**
     * 获取指定视角下的最新答案（用于获取该视角当前的难度）
     */
    public Optional<InterviewAnswerEntity> findLastAnswerBySessionAndPerspective(Long sessionDbId, Long perspectiveId) {
        return answerRepository.findLastAnswerBySessionAndPerspective(sessionDbId, perspectiveId);
    }
}

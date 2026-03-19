package interview.guide.modules.interview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.infrastructure.redis.InterviewSessionCache.CachedSession;
import interview.guide.modules.interview.listener.EvaluateStreamProducer;
import interview.guide.modules.interview.model.*;
import interview.guide.modules.interview.model.InterviewSessionDTO.SessionStatus;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import interview.guide.modules.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 面试会话管理服务
 * 管理面试会话的生命周期，使用 Redis 缓存会话状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private final InterviewQuestionService questionService;
    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSessionCache sessionCache;
    private final ObjectMapper objectMapper;
    private final EvaluateStreamProducer evaluateStreamProducer;
    private final KnowledgeBaseVectorService knowledgeBaseVectorService;
    private final DifficultyAdjustmentService difficultyAdjustmentService;
    private final QuestionGenerationService questionGenerationService;
    private final SingleAnswerEvaluationService singleAnswerEvaluationService;

    @Lazy
    private QuestionService questionServiceForBank;

    /**
     * 创建新的面试会话
     * 注意：如果已有未完成的会话，不会创建新的，而是返回现有会话
     * 前端应该先调用 findUnfinishedSession 检查，或者使用 forceCreate 参数强制创建
     */
    public InterviewSessionBasicDTO createSession(Long userId, CreateInterviewRequest request) {
        // 如果指定了resumeId且未强制创建，检查是否有未完成的会话
        if (request.resumeId() != null && !Boolean.TRUE.equals(request.forceCreate())) {
            Optional<InterviewSessionBasicDTO> unfinishedOpt = findUnfinishedSession(userId, request.resumeId());
            if (unfinishedOpt.isPresent()) {
                log.info("检测到未完成的面试会话，返回现有会话: userId={}, resumeId={}, sessionId={}",
                        userId, request.resumeId(), unfinishedOpt.get().sessionId());
                return unfinishedOpt.get();
            }
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        log.info("创建新面试会话: userId={}, sessionId={}, 题目数量: {}, resumeId: {}, knowledgeBaseIds: {}",
                userId, sessionId, request.questionCount(), request.resumeId(), request.knowledgeBaseIds());

        // 自适应难度模式：始终不批量生成问题，问题在 getCurrentQuestionForAdaptive 中实时生成
        // 保存知识库ID列表到会话
        String knowledgeBaseIdsJson = null;
        if (request.knowledgeBaseIds() != null && !request.knowledgeBaseIds().isEmpty()) {
            try {
                knowledgeBaseIdsJson = objectMapper.writeValueAsString(request.knowledgeBaseIds());
            } catch (Exception e) {
                log.warn("序列化知识库ID失败: {}", e.getMessage());
            }
        }

        // 保存到数据库（不生成问题）
        persistenceService.saveAdaptiveSession(userId, sessionId, request.resumeId(),
                request.questionCount(), knowledgeBaseIdsJson);

        return new InterviewSessionBasicDTO(
                sessionId,
                request.resumeText(),
                request.questionCount(),
                0,
                "CREATED",
                "BASIC",
                null,
                0
        );
    }

    /**
     * 获取会话信息（优先从缓存获取，缓存未命中则从数据库恢复）
     */
    public InterviewSessionBasicDTO getSession(Long userId, String sessionId) {
        // 验证会话所有权
        validateSessionOwnership(userId, sessionId);

        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            return toDTO(cachedOpt.get());
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return toDTO(restoredSession);
    }

    /**
     * 验证会话是否属于当前用户
     */
    public void validateSessionOwnership(Long userId, String sessionId) {
        // 从数据库查询会话
        Optional<InterviewSessionEntity> sessionOpt = persistenceService.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        InterviewSessionEntity session = sessionOpt.get();
        // 通过简历验证用户身份
        Long resumeUserId = session.getResume().getUserId();
        if (resumeUserId == null || !resumeUserId.equals(userId)) {
            log.warn("用户 {} 尝试访问不属于他的会话 {}", userId, sessionId);
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该面试会话");
        }
    }

    /**
     * 获取面试评估状态（轻量级接口）
     */
    public InterviewEvaluateStatusDTO getEvaluateStatus(String sessionId) {
        Optional<InterviewSessionEntity> sessionOpt = persistenceService.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        InterviewSessionEntity session = sessionOpt.get();
        return new InterviewEvaluateStatusDTO(
                sessionId,
                session.getOverallScore(),
                session.getEvaluateStatus() != null ? session.getEvaluateStatus().name() : null
        );
    }

    /**
     * 查找并恢复未完成的面试会话
     */
    public Optional<InterviewSessionBasicDTO> findUnfinishedSession(Long userId, Long resumeId) {
        try {
            // 1. 先从 Redis 缓存查找
            Optional<String> cachedSessionIdOpt = sessionCache.findUnfinishedSessionId(resumeId);
            if (cachedSessionIdOpt.isPresent()) {
                String sessionId = cachedSessionIdOpt.get();
                Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
                if (cachedOpt.isPresent()) {
                    // 验证所有权
                    InterviewSessionEntity entity = persistenceService.findBySessionId(sessionId).orElse(null);
                    if (entity != null && userId.equals(entity.getResume().getUserId())) {
                        log.debug("从 Redis 缓存找到未完成会话: userId={}, resumeId={}, sessionId={}", userId, resumeId, sessionId);
                        return Optional.of(toDTO(cachedOpt.get()));
                    }
                }
            }

            // 2. 缓存未命中，从数据库查找
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findUnfinishedSession(resumeId);
            if (entityOpt.isEmpty()) {
                return Optional.empty();
            }

            InterviewSessionEntity entity = entityOpt.get();
            // 验证所有权
            if (!userId.equals(entity.getResume().getUserId())) {
                return Optional.empty();
            }

            CachedSession restoredSession = restoreSessionFromEntity(entity);
            if (restoredSession != null) {
                return Optional.of(toDTO(restoredSession));
            }
        } catch (Exception e) {
            log.error("恢复未完成会话失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * 查找并恢复未完成的面试会话，如果不存在则抛出异常
     */
    public InterviewSessionBasicDTO findUnfinishedSessionOrThrow(Long userId, Long resumeId) {
        return findUnfinishedSession(userId, resumeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND, "未找到未完成的面试会话"));
    }

    /**
     * 从数据库恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromDatabase(String sessionId) {
        try {
            Optional<InterviewSessionEntity> entityOpt = persistenceService.findBySessionId(sessionId);
            return entityOpt.map(this::restoreSessionFromEntity).orElse(null);
        } catch (Exception e) {
            log.error("从数据库恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从实体恢复会话并缓存到 Redis
     */
    private CachedSession restoreSessionFromEntity(InterviewSessionEntity entity) {
        try {
            // 解析问题列表
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                    entity.getQuestionsJson(),
                    new TypeReference<>() {
                    }
            );

            // 恢复已保存的答案
            List<InterviewAnswerEntity> answers = persistenceService.findAnswersBySessionId(entity.getSessionId());
            for (InterviewAnswerEntity answer : answers) {
                int index = answer.getQuestionIndex();
                if (index >= 0 && index < questions.size()) {
                    InterviewQuestionDTO question = questions.get(index);
                    questions.set(index, question.withAnswer(answer.getUserAnswer()));
                }
            }

            SessionStatus status = convertStatus(entity.getStatus());

            // 保存到 Redis 缓存
            sessionCache.saveSession(
                    entity.getSessionId(),
                    entity.getResume().getResumeText(),
                    entity.getResume().getId(),
                    questions,
                    entity.getCurrentQuestionIndex(),
                    status
            );

            log.info("从数据库恢复会话到 Redis: sessionId={}, currentIndex={}, status={}",
                    entity.getSessionId(), entity.getCurrentQuestionIndex(), entity.getStatus());

            // 返回缓存的会话
            return sessionCache.getSession(entity.getSessionId()).orElse(null);
        } catch (Exception e) {
            log.error("恢复会话失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private SessionStatus convertStatus(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case CREATED -> SessionStatus.CREATED;
            case IN_PROGRESS -> SessionStatus.IN_PROGRESS;
            case COMPLETED -> SessionStatus.COMPLETED;
            case EVALUATED -> SessionStatus.EVALUATED;
        };
    }

    /**
     * 获取当前问题的响应（包含完成状态）
     */
    public Map<String, Object> getCurrentQuestionResponse(String sessionId) {
        InterviewQuestionDTO question = getCurrentQuestion(sessionId);
        if (question == null) {
            return Map.of(
                    "completed", true,
                    "message", "所有问题已回答完毕"
            );
        }
        return Map.of(
                "completed", false,
                "question", question
        );
    }

    /**
     * 获取当前问题
     */
    public InterviewQuestionDTO getCurrentQuestion(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        if (session.getCurrentIndex() >= questions.size()) {
            return null; // 所有问题已回答完
        }

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            sessionCache.updateSessionStatus(sessionId, SessionStatus.IN_PROGRESS);

            // 同步到数据库
            try {
                persistenceService.updateSessionStatus(sessionId,
                        InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            } catch (Exception e) {
                log.warn("更新会话状态失败: {}", e.getMessage());
            }
        }

        return questions.get(session.getCurrentIndex());
    }

    /**
     * 提交答案（并进入下一题）
     * 如果是最后一题，自动触发异步评估
     */
    public SubmitAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        Result result = getResult(request);

        // 移动到下一题
        int newIndex = result.index() + 1;

        // 检查是否全部完成
        boolean hasNextQuestion = newIndex < result.questions().size();
        InterviewQuestionDTO nextQuestion = hasNextQuestion ? result.questions().get(newIndex) : null;

        SessionStatus newStatus = hasNextQuestion ? SessionStatus.IN_PROGRESS : SessionStatus.COMPLETED;

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), result.questions());
        sessionCache.updateCurrentIndex(request.sessionId(), newIndex);
        if (newStatus == SessionStatus.COMPLETED) {
            sessionCache.updateSessionStatus(request.sessionId(), SessionStatus.COMPLETED);
        }

        // 保存答案到数据库
        try {
            persistenceService.saveAnswer(
                    request.sessionId(), result.index(),
                    result.question().question(), result.question().category(),
                    request.answer(), 0, null  // 分数在报告生成时更新
            );
            persistenceService.updateCurrentQuestionIndex(request.sessionId(), newIndex);
            persistenceService.updateSessionStatus(request.sessionId(),
                    newStatus == SessionStatus.COMPLETED
                            ? InterviewSessionEntity.SessionStatus.COMPLETED
                            : InterviewSessionEntity.SessionStatus.IN_PROGRESS);

            // 如果是最后一题，设置评估状态为 PENDING 并触发异步评估
            if (!hasNextQuestion) {
                persistenceService.updateEvaluateStatus(request.sessionId(), AsyncTaskStatus.PENDING, null);
                evaluateStreamProducer.sendEvaluateTask(request.sessionId());
                log.info("会话 {} 已完成所有问题，评估任务已入队", request.sessionId());
            }
        } catch (Exception e) {
            log.warn("保存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 提交答案: 问题{}, 剩余{}题",
                request.sessionId(), result.index(), result.questions().size() - newIndex);

        return new SubmitAnswerResponse(
                hasNextQuestion,
                null,
                newIndex,
                result.questions().size(),
                0,
                Map.of(),
                "BASIC"
        );
    }

    private Result getResult(SubmitAnswerRequest request) {
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);
        Result result = new Result(questions, index, question);
        return result;
    }

    private record Result(List<InterviewQuestionDTO> questions, int index, InterviewQuestionDTO question) {
    }

    /**
     * 暂存答案（不进入下一题）
     */
    public void saveAnswer(SubmitAnswerRequest request) {
        CachedSession session = getOrRestoreSession(request.sessionId());
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        int index = request.questionIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "无效的问题索引: " + index);
        }

        // 更新问题答案
        InterviewQuestionDTO question = questions.get(index);
        InterviewQuestionDTO answeredQuestion = question.withAnswer(request.answer());
        questions.set(index, answeredQuestion);

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), questions);

        // 更新状态为进行中
        if (session.getStatus() == SessionStatus.CREATED) {
            sessionCache.updateSessionStatus(request.sessionId(), SessionStatus.IN_PROGRESS);
        }

        // 保存答案到数据库（不更新currentIndex）
        try {
            persistenceService.saveAnswer(
                    request.sessionId(), index,
                    question.question(), question.category(),
                    request.answer(), 0, null
            );
            persistenceService.updateSessionStatus(request.sessionId(),
                    InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        } catch (Exception e) {
            log.warn("暂存答案到数据库失败: {}", e.getMessage());
        }

        log.info("会话 {} 暂存答案: 问题{}", request.sessionId(), index);
    }

    /**
     * 提前交卷（触发异步评估）
     */
    public void completeInterview(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        // 更新 Redis 缓存
        sessionCache.updateSessionStatus(sessionId, SessionStatus.COMPLETED);

        // 更新数据库状态
        try {
            persistenceService.updateSessionStatus(sessionId,
                    InterviewSessionEntity.SessionStatus.COMPLETED);
            // 设置评估状态为 PENDING
            persistenceService.updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
        } catch (Exception e) {
            log.warn("更新会话状态失败: {}", e.getMessage());
        }

        // 发送评估任务到 Redis Stream
        evaluateStreamProducer.sendEvaluateTask(sessionId);

        log.info("会话 {} 提前交卷，评估任务已入队", sessionId);
    }

    /**
     * 获取或恢复会话（优先从缓存获取）
     */
    private CachedSession getOrRestoreSession(String sessionId) {
        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            // 刷新 TTL
            sessionCache.refreshSessionTTL(sessionId);
            return cachedOpt.get();
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return restoredSession;
    }

    /**
     * 生成评估报告
     */
    public InterviewReportDTO generateReport(String sessionId) {
        CachedSession session = getOrRestoreSession(sessionId);

        if (session.getStatus() != SessionStatus.COMPLETED && session.getStatus() != SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_NOT_COMPLETED, "面试尚未完成，无法生成报告");
        }

        log.info("生成面试报告: {}", sessionId);

        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);

        InterviewReportDTO report = evaluationService.evaluateInterview(
                sessionId,
                session.getResumeText(),
                questions
        );

        // 更新 Redis 缓存状态
        sessionCache.updateSessionStatus(sessionId, SessionStatus.EVALUATED);

        // 保存报告到数据库
        try {
            persistenceService.saveReport(sessionId, report);
        } catch (Exception e) {
            log.warn("保存报告到数据库失败: {}", e.getMessage());
        }

        return report;
    }

    /**
     * 将缓存会话转换为 DTO（不包含问题列表）
     */
    private InterviewSessionBasicDTO toDTO(CachedSession session) {
        // 从 questionsJson 获取问题数量
        int totalQuestions = 0;
        try {
            if (session.getQuestionsJson() != null && !session.getQuestionsJson().isBlank()) {
                List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
                totalQuestions = questions.size();
            }
        } catch (Exception e) {
            log.warn("解析问题列表失败: {}", e.getMessage());
        }

        return new InterviewSessionBasicDTO(
                session.getSessionId(),
                session.getResumeText(),
                totalQuestions,
                session.getCurrentIndex(),
                session.getStatus().name(),
                "BASIC",
                null,
                0
        );
    }

    /**
     * 切换面试知识库
     * 会根据新的知识库重新生成所有未回答的问题
     */
    public InterviewSessionBasicDTO switchKnowledgeBase(Long userId, String sessionId, List<Long> knowledgeBaseIds) {
        // 验证会话所有权
        validateSessionOwnership(userId, sessionId);

        // 获取会话
        CachedSession session = getOrRestoreSession(sessionId);

        // 检查面试状态，只有未完成的面试才能切换知识库
        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED, "面试已完成，无法切换知识库");
        }

        log.info("切换面试知识库: sessionId={}, 新知识库IDs={}, 当前进度={}/{}}",
                sessionId, knowledgeBaseIds, session.getCurrentIndex(), session.getQuestions(objectMapper).size());

        // 获取知识库内容
        String knowledgeBaseContext = retrieveKnowledgeBaseContext(knowledgeBaseIds);

        // 获取已回答的问题（保留答案）
        List<InterviewQuestionDTO> answeredQuestions = new ArrayList<>();
        List<InterviewQuestionDTO> allQuestions = session.getQuestions(objectMapper);
        for (int i = 0; i < session.getCurrentIndex(); i++) {
            if (i < allQuestions.size() && allQuestions.get(i).userAnswer() != null) {
                answeredQuestions.add(allQuestions.get(i));
            }
        }

        // 重新生成剩余问题
        List<InterviewQuestionDTO> newQuestions;
        if (knowledgeBaseContext != null) {
            newQuestions = questionService.generateQuestionsWithContext(
                    session.getResumeText(),
                    allQuestions.size(),
                    knowledgeBaseContext,
                    null
            );
        } else {
            newQuestions = questionService.generateQuestions(
                    session.getResumeText(),
                    allQuestions.size()
            );
        }

        // 合并已回答的问题和新生成的问题
        int answeredCount = answeredQuestions.size();
        for (int i = 0; i < answeredCount && i < newQuestions.size(); i++) {
            newQuestions.set(i, answeredQuestions.get(i));
        }

        // 更新缓存
        sessionCache.saveSession(
                sessionId,
                session.getResumeText(),
                session.getResumeId(),
                newQuestions,
                session.getCurrentIndex(),
                session.getStatus(),
                knowledgeBaseIds
        );

        // 尝试更新数据库（如果会话已持久化）
        try {
            persistenceService.updateQuestions(sessionId, newQuestions);
        } catch (Exception e) {
            log.warn("更新数据库问题列表失败: {}", e.getMessage());
        }

        log.info("切换知识库完成: sessionId={}, 新问题数量={}", sessionId, newQuestions.size());

        return toDTO(sessionCache.getSession(sessionId).orElseThrow(
                () -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND)
        ));
    }

    /**
     * 从知识库检索相关内容作为上下文
     */
    private String retrieveKnowledgeBaseContext(List<Long> knowledgeBaseIds) {
        try {
            // 搜索与简历相关的知识库内容
            // 这里使用一个通用查询来获取知识库的摘要信息
            List<Document> docs = knowledgeBaseVectorService.similaritySearch(
                    "面试题 技术知识 项目经验",
                    knowledgeBaseIds,
                    10,
                    0
            );

            if (docs.isEmpty()) {
                log.info("知识库检索结果为空");
                return null;
            }

            // 合并检索到的文档内容
            String context = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            log.info("从知识库检索到 {} 个相关文档片段", docs.size());
            return context;
        } catch (Exception e) {
            log.warn("从知识库检索内容失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前问题（自适应难度版本）
     * 实时生成问题并入库，返回 CurrentQuestionDTO
     */
    public CurrentQuestionDTO getCurrentQuestionForAdaptive(String sessionId) {
        // 获取会话实体
        InterviewSessionEntity session = persistenceService.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        // 获取当前简历文本（可能没有简历）
        String resumeText = null;
        if (session.getResume() != null) {
            resumeText = session.getResume().getResumeText();
        }
        if (resumeText == null || resumeText.isBlank()) {
            resumeText = "通用面试，无特定简历内容";
        }

        // 计算当前问题索引
        int questionIndex = session.getCurrentQuestionIndex() != null ? session.getCurrentQuestionIndex() : 0;

        // 判断是否已超出总题数
        if (session.getTotalQuestions() != null && questionIndex >= session.getTotalQuestions()) {
            return null; // 所有问题已回答完毕
        }

        // 确定问题分类（轮询各个分类）
        String category = getNextCategory(questionIndex);

        // 生成问题
        CurrentQuestionDTO questionDTO = questionGenerationService.generateSingleQuestion(
                session, questionIndex, resumeText, category);

        // 保存生成的问题到数据库（userAnswer为null，等待用户回答后更新）
        persistenceService.saveAnswerWithDifficulty(
                sessionId,
                questionIndex,
                questionDTO.getQuestion(),
                category,
                null, // userAnswer为null，等待用户回答后更新
                questionDTO.getDifficulty(),
                questionDTO.getKnowledgeBaseId(),
                questionDTO.getReferenceContext(),
                0,  // 初始评分为0，等待用户回答后更新
                null // 初始反馈为null
        );

        // 更新会话状态
        if (session.getStatus() == InterviewSessionEntity.SessionStatus.CREATED) {
            session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            persistenceService.updateSessionStatus(sessionId, InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        }

        log.info("获取当前问题: sessionId={}, index={}, category={}, difficulty={}",
                sessionId, questionIndex, category, questionDTO.getDifficulty());

        return questionDTO;
    }

    /**
     * 获取下一个问题分类
     */
    private String getNextCategory(int questionIndex) {
        String[] categories = {
                "Java基础", "Java集合", "Java并发",
                "MySQL", "Redis", "Spring", "项目经历"
        };
        return categories[questionIndex % categories.length];
    }

    /**
     * 提交答案（自适应难度版本）
     * 保存答案 -> AI评估 -> 更新分类得分 -> 调整难度 -> 返回下一题
     */
    public SubmitAnswerResponse submitAnswerForAdaptive(String sessionId, Integer questionIndex, String answer) {
        // 获取会话实体
        InterviewSessionEntity session = persistenceService.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        // 获取当前问题
        List<InterviewAnswerEntity> existingAnswers = persistenceService.findAnswersBySessionId(sessionId);
        InterviewAnswerEntity currentAnswer = existingAnswers.stream()
                .filter(a -> a.getQuestionIndex().equals(questionIndex))
                .findFirst()
                .orElse(null);

        if (currentAnswer == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "问题不存在");
        }

        String resumeText = session.getResume().getResumeText();
        if (resumeText == null || resumeText.isBlank()) {
            resumeText = "通用面试，无特定简历内容";
        }

        // 评估答案
        SingleAnswerEvaluationService.EvaluationResult evaluationResult = singleAnswerEvaluationService.evaluateAnswer(
                currentAnswer.getQuestion(),
                currentAnswer.getCategory(),
                currentAnswer.getDifficulty(),
                answer,
                resumeText,
                currentAnswer.getReferenceContext()
        );

        // 保存答案（含评估结果）
        persistenceService.saveAnswerWithDifficulty(
                sessionId,
                questionIndex,
                currentAnswer.getQuestion(),
                currentAnswer.getCategory(),
                answer,
                currentAnswer.getDifficulty(),
                currentAnswer.getKnowledgeBaseId(),
                currentAnswer.getReferenceContext(),
                evaluationResult.score(),
                evaluationResult.feedback()
        );

        // 更新分类得分
        Map<String, CategoryScoreDTO> categoryScores = calculateCategoryScores(sessionId);
        try {
            session.setCategoryScores(objectMapper.writeValueAsString(categoryScores));
            persistenceService.updateCategoryScores(sessionId, session.getCategoryScores());
        } catch (Exception e) {
            log.warn("序列化分类得分失败: {}", e.getMessage());
        }

        // 计算当前总分
        int currentScore = calculateOverallScore(categoryScores);

        // 调整难度
        String currentDifficulty = session.getCurrentDifficulty() != null ? session.getCurrentDifficulty() : "BASIC";
        String nextDifficulty = difficultyAdjustmentService.adjustDifficulty(currentDifficulty, evaluationResult.score());
        session.setCurrentDifficulty(nextDifficulty);
        persistenceService.updateCurrentDifficulty(sessionId, nextDifficulty);

        // 更新问题索引
        int newIndex = questionIndex + 1;
        boolean hasNextQuestion = session.getTotalQuestions() == null || newIndex < session.getTotalQuestions();

        // 更新会话状态
        persistenceService.updateCurrentQuestionIndex(sessionId, newIndex);

        CurrentQuestionDTO nextQuestion = null;
        if (hasNextQuestion) {
            // 生成下一题
            session.setCurrentQuestionIndex(newIndex);
            nextQuestion = questionGenerationService.generateSingleQuestion(
                    session, newIndex, resumeText, getNextCategory(newIndex));
        } else {
            // 面试完成，触发评估
            session.setStatus(InterviewSessionEntity.SessionStatus.COMPLETED);
            persistenceService.updateSessionStatus(sessionId, InterviewSessionEntity.SessionStatus.COMPLETED);
            persistenceService.updateEvaluateStatus(sessionId, AsyncTaskStatus.PENDING, null);
            evaluateStreamProducer.sendEvaluateTask(sessionId);
            log.info("会话 {} 已完成所有问题，评估任务已入队", sessionId);
        }

        // 更新已生成问题数量
        int generated = session.getQuestionsGenerated() != null ? session.getQuestionsGenerated() : 0;
        persistenceService.updateQuestionsGenerated(sessionId, generated);

        log.info("提交答案完成: sessionId={}, questionIndex={}, score={}, nextDifficulty={}, hasNext={}",
                sessionId, questionIndex, evaluationResult.score(), nextDifficulty, hasNextQuestion);

        return new SubmitAnswerResponse(
                hasNextQuestion,
                nextQuestion,
                newIndex,
                generated,
                currentScore,
                categoryScores,
                nextDifficulty
        );
    }

    /**
     * 计算分类得分（返回Map格式）
     */
    private Map<String, CategoryScoreDTO> calculateCategoryScores(String sessionId) {
        List<InterviewAnswerEntity> scoredAnswers = persistenceService.findScoredAnswersBySessionId(sessionId);

        Map<String, List<Integer>> categoryScoresMap = new LinkedHashMap<>();
        for (InterviewAnswerEntity answer : scoredAnswers) {
            String category = answer.getCategory();
            if (category != null && !category.isBlank()) {
                // 提取基础分类（去掉追问标记）
                String baseCategory = category.contains("（追问") ?
                        category.substring(0, category.indexOf("（追问")) : category;

                categoryScoresMap.computeIfAbsent(baseCategory, k -> new ArrayList<>())
                        .add(answer.getScore());
            }
        }

        Map<String, CategoryScoreDTO> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : categoryScoresMap.entrySet()) {
            List<Integer> scores = entry.getValue();
            int total = scores.stream().mapToInt(Integer::intValue).sum();
            int avg = (int) scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            result.put(entry.getKey(), new CategoryScoreDTO(entry.getKey(), total, scores.size(), avg));
        }

        return result;
    }

    /**
     * 计算总分（适配Map格式）
     */
    private int calculateOverallScore(Map<String, CategoryScoreDTO> categoryScores) {
        if (categoryScores == null || categoryScores.isEmpty()) {
            return 0;
        }
        return (int) categoryScores.values().stream()
                .mapToInt(CategoryScoreDTO::getAvgScore)
                .average()
                .orElse(0);
    }

    /**
     * 获取能力画像
     */
    public AbilityProfileDTO getAbilityProfile(String sessionId) {
        InterviewSessionEntity session = persistenceService.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        // 计算分类得分
        Map<String, CategoryScoreDTO> categoryScores = calculateCategoryScores(sessionId);

        // 计算总分
        int overallScore = calculateOverallScore(categoryScores);

        // 提取优势和劣势
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();

        for (CategoryScoreDTO cs : categoryScores.values()) {
            int score = cs.getAvgScore();
            if (score >= 80) {
                strengths.add(cs.getCategory());
            } else if (score < 60) {
                weaknesses.add(cs.getCategory());
            }
        }

        log.info("获取能力画像: sessionId={}, overallScore={}, categoryCount={}",
                sessionId, overallScore, categoryScores.size());

        return new AbilityProfileDTO(categoryScores, overallScore, strengths, weaknesses);
    }

}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    @Lazy
    private QuestionService questionServiceForBank;

    /**
     * 创建新的面试会话
     * 注意：如果已有未完成的会话，不会创建新的，而是返回现有会话
     * 前端应该先调用 findUnfinishedSession 检查，或者使用 forceCreate 参数强制创建
     */
    public InterviewSessionDTO createSession(Long userId, CreateInterviewRequest request) {
        // 如果指定了resumeId且未强制创建，检查是否有未完成的会话
        if (request.resumeId() != null && !Boolean.TRUE.equals(request.forceCreate())) {
            Optional<InterviewSessionDTO> unfinishedOpt = findUnfinishedSession(userId, request.resumeId());
            if (unfinishedOpt.isPresent()) {
                log.info("检测到未完成的面试会话，返回现有会话: userId={}, resumeId={}, sessionId={}",
                    userId, request.resumeId(), unfinishedOpt.get().sessionId());
                return unfinishedOpt.get();
            }
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        log.info("创建新面试会话: userId={}, sessionId={}, 题目数量: {}, resumeId: {}, questionBankIds: {}, knowledgeBaseIds: {}",
            userId, sessionId, request.questionCount(), request.resumeId(), request.questionBankIds(), request.knowledgeBaseIds());

        // 获取知识库内容（如果有指定知识库）
        String knowledgeBaseContext = null;
        if (request.knowledgeBaseIds() != null && !request.knowledgeBaseIds().isEmpty()) {
            knowledgeBaseContext = retrieveKnowledgeBaseContext(request.knowledgeBaseIds());
            log.info("已从知识库获取上下文内容，长度: {}", knowledgeBaseContext != null ? knowledgeBaseContext.length() : 0);
        }

        // 生成面试问题
        List<InterviewQuestionDTO> questions;

        // 如果指定了题库，从题库获取题目；否则使用 AI 生成
        if (request.questionBankIds() != null && !request.questionBankIds().isEmpty()) {
            log.info("从题库获取题目: bankIds={}", request.questionBankIds());
            var bankQuestions = questionServiceForBank.getRandomQuestionsFromBanks(
                request.questionBankIds(),
                request.questionCount()
            );
            var questionList = bankQuestions.getData();
            questions = new java.util.ArrayList<InterviewQuestionDTO>();
            for (int i = 0; i < questionList.size(); i++) {
                var q = questionList.get(i);
                InterviewQuestionDTO dto = InterviewQuestionDTO.create(
                    i,
                    q.getContent(),
                    InterviewQuestionDTO.QuestionType.JAVA_BASIC,
                    q.getDifficulty() != null ? q.getDifficulty().name() : "MEDIUM"
                );
                questions.add(dto);
            }
        } else if (knowledgeBaseContext != null) {
            // 使用知识库内容生成问题
            log.info("使用知识库内容生成面试问题");
            questions = questionService.generateQuestionsWithContext(
                request.resumeText(),
                request.questionCount(),
                knowledgeBaseContext
            );
        } else {
            // 使用 AI 生成题目
            questions = questionService.generateQuestions(
                request.resumeText(),
                request.questionCount()
            );
        }

        // 保存到 Redis 缓存（包含知识库ID）
        sessionCache.saveSession(
            sessionId,
            request.resumeText(),
            request.resumeId(),
            questions,
            0,
            SessionStatus.CREATED,
            request.knowledgeBaseIds()
        );

        // 保存到数据库（关联用户ID）
        if (request.resumeId() != null) {
            try {
                persistenceService.saveSession(userId, sessionId, request.resumeId(),
                    request.questionCount(), questions);
            } catch (Exception e) {
                log.warn("保存面试会话到数据库失败: {}", e.getMessage());
            }
        }

        return new InterviewSessionDTO(
            sessionId,
            request.resumeText(),
            questions.size(),
            0,
            questions,
            SessionStatus.CREATED,
            request.knowledgeBaseIds()
        );
    }

    /**
     * 获取会话信息（优先从缓存获取，缓存未命中则从数据库恢复）
     */
    public InterviewSessionDTO getSession(Long userId, String sessionId) {
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
     * 查找并恢复未完成的面试会话
     */
    public Optional<InterviewSessionDTO> findUnfinishedSession(Long userId, Long resumeId) {
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
    public InterviewSessionDTO findUnfinishedSessionOrThrow(Long userId, Long resumeId) {
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
                new TypeReference<>() {}
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

        // 移动到下一题
        int newIndex = index + 1;

        // 检查是否全部完成
        boolean hasNextQuestion = newIndex < questions.size();
        InterviewQuestionDTO nextQuestion = hasNextQuestion ? questions.get(newIndex) : null;

        SessionStatus newStatus = hasNextQuestion ? SessionStatus.IN_PROGRESS : SessionStatus.COMPLETED;

        // 更新 Redis 缓存
        sessionCache.updateQuestions(request.sessionId(), questions);
        sessionCache.updateCurrentIndex(request.sessionId(), newIndex);
        if (newStatus == SessionStatus.COMPLETED) {
            sessionCache.updateSessionStatus(request.sessionId(), SessionStatus.COMPLETED);
        }

        // 保存答案到数据库
        try {
            persistenceService.saveAnswer(
                request.sessionId(), index,
                question.question(), question.category(),
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
            request.sessionId(), index, questions.size() - newIndex);

        return new SubmitAnswerResponse(
            hasNextQuestion,
            nextQuestion,
            newIndex,
            questions.size()
        );
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
     * 将缓存会话转换为 DTO
     */
    private InterviewSessionDTO toDTO(CachedSession session) {
        List<InterviewQuestionDTO> questions = session.getQuestions(objectMapper);
        return new InterviewSessionDTO(
            session.getSessionId(),
            session.getResumeText(),
            questions.size(),
            session.getCurrentIndex(),
            questions,
            session.getStatus(),
            session.getKnowledgeBaseIds()
        );
    }

    /**
     * 切换面试知识库
     * 会根据新的知识库重新生成所有未回答的问题
     */
    public InterviewSessionDTO switchKnowledgeBase(Long userId, String sessionId, List<Long> knowledgeBaseIds) {
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
                knowledgeBaseContext
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
                10
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
}

package interview.guide.modules.interview.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.infrastructure.redis.InterviewSessionCache.CachedSession;
import interview.guide.modules.interview.listener.EvaluateStreamProducer;
import interview.guide.modules.interview.model.*;
import interview.guide.modules.interview.model.InterviewSessionDTO.SessionStatus;
import interview.guide.modules.interview.repository.InterviewerRoleRepository;
import interview.guide.modules.interview.workflow.WorkflowExecutor;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.ai.document.Document;
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

    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSessionCache sessionCache;
    private final ObjectMapper objectMapper;
    private final EvaluateStreamProducer evaluateStreamProducer;
    private final KnowledgeBaseVectorService knowledgeBaseVectorService;
    private final DifficultyAdjustmentService difficultyAdjustmentService;
    private final QuestionGenerationService questionGenerationService;
    private final SingleAnswerEvaluationService singleAnswerEvaluationService;
    private final PerspectiveEvaluationService perspectiveEvaluationService;
    private final InterviewerRoleRepository interviewerRoleRepository;
    private final WorkflowExecutor workflowExecutor;

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

        log.info("创建新面试会话: userId={}, sessionId={}, 题目数量: {}, resumeId: {}, knowledgeBaseIds: {}, selectedPerspectives: {}",
                userId, sessionId, request.questionCount(), request.resumeId(), request.knowledgeBaseIds(), request.selectedPerspectives());

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

        // 保存选择的视角列表
        String selectedPerspectivesJson = null;
        if (request.selectedPerspectives() != null && !request.selectedPerspectives().isEmpty()) {
            try {
                selectedPerspectivesJson = objectMapper.writeValueAsString(request.selectedPerspectives());
            } catch (Exception e) {
                log.warn("序列化选择视角失败: {}", e.getMessage());
            }
        }

        // 保存视角权重配置（会话级权重）
        String perspectiveWeightsJson = null;
        if (request.perspectiveWeights() != null && !request.perspectiveWeights().isEmpty()) {
            try {
                perspectiveWeightsJson = objectMapper.writeValueAsString(request.perspectiveWeights());
            } catch (Exception e) {
                log.warn("序列化视角权重失败: {}", e.getMessage());
            }
        }

        // 保存到数据库（不生成问题）
        persistenceService.saveAdaptiveSession(userId, sessionId, request.resumeId(),
                request.questionCount(), knowledgeBaseIdsJson, selectedPerspectivesJson, perspectiveWeightsJson);

        return new InterviewSessionBasicDTO(
                sessionId,
                request.resumeText(),
                request.questionCount(),
                0,
                "CREATED",
                "BASIC",
                null,
                0,
                0  // 新创建的会话，已答题数量为0
        );
    }

    /**
     * 获取会话信息（优先从缓存获取，缓存未命中则从数据库恢复）
     */
    public InterviewSessionBasicDTO getSession(Long userId, String sessionId) {
        // 验证会话所有权
        validateSessionOwnership(userId, sessionId);

        // 计算已答题数量
        int answeredCount = persistenceService.findAnswersBySessionId(sessionId).stream()
                .filter(a -> a.getUserAnswer() != null && !a.getUserAnswer().isBlank())
                .toList().size();

        // 1. 尝试从 Redis 缓存获取
        Optional<CachedSession> cachedOpt = sessionCache.getSession(sessionId);
        if (cachedOpt.isPresent()) {
            return toDTO(cachedOpt.get(), answeredCount);
        }

        // 2. 缓存未命中，从数据库恢复
        CachedSession restoredSession = restoreSessionFromDatabase(sessionId);
        if (restoredSession == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }

        return toDTO(restoredSession, answeredCount);
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
                        // 计算已答题数量（userAnswer 不为空的答案）
                        int answeredCount = persistenceService.findAnswersBySessionId(sessionId).stream()
                                .filter(a -> a.getUserAnswer() != null && !a.getUserAnswer().isBlank())
                                .toList().size();
                        log.debug("从 Redis 缓存找到未完成会话: userId={}, resumeId={}, sessionId={}, answeredCount={}", userId, resumeId, sessionId, answeredCount);
                        return Optional.of(toDTO(cachedOpt.get(), answeredCount));
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

            // 计算已答题数量
            int answeredCount = persistenceService.findAnswersBySessionId(entity.getSessionId()).stream()
                    .filter(a -> a.getUserAnswer() != null && !a.getUserAnswer().isBlank())
                    .toList().size();

            CachedSession restoredSession = restoreSessionFromEntity(entity);
            if (restoredSession != null) {
                return Optional.of(toDTO(restoredSession, answeredCount));
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
     * 从实体恢复会话（自适应面试版本）
     * 自适应面试的问题是一题一生成的，questionsJson 始终为 null
     */
    private CachedSession restoreSessionFromEntity(InterviewSessionEntity entity) {
        try {
            SessionStatus status = convertStatus(entity.getStatus());
            String resumeText = entity.getResume() != null ? entity.getResume().getResumeText() : null;
            Long resumeId = entity.getResume() != null ? entity.getResume().getId() : null;

            // 构建 CachedSession 对象
            CachedSession cachedSession = new CachedSession();
            cachedSession.setSessionId(entity.getSessionId());
            cachedSession.setResumeText(resumeText);
            cachedSession.setResumeId(resumeId);
            cachedSession.setCurrentIndex(entity.getCurrentQuestionIndex() != null ? entity.getCurrentQuestionIndex() : 0);
            cachedSession.setStatus(status);
            cachedSession.setQuestionsGenerated(entity.getQuestionsGenerated() != null ? entity.getQuestionsGenerated() : 0);
            cachedSession.setTotalQuestions(entity.getTotalQuestions() != null ? entity.getTotalQuestions() : 0);

            // 解析并设置知识库ID列表
            if (entity.getKnowledgeBaseIds() != null && !entity.getKnowledgeBaseIds().isBlank()) {
                try {
                    cachedSession.setKnowledgeBaseIds(objectMapper.readValue(entity.getKnowledgeBaseIds(), new TypeReference<List<Long>>() {}));
                } catch (Exception e) {
                    log.warn("解析知识库ID失败: {}", e.getMessage());
                }
            }

            log.info("自适应会话恢复: sessionId={}, currentIndex={}, status={}",
                    entity.getSessionId(), entity.getCurrentQuestionIndex(), entity.getStatus());
            return cachedSession;
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

        // 从数据库获取问题和答案（自适应面试）
        List<InterviewQuestionDTO> questions = buildQuestionsFromAnswers(sessionId);

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
     * 从答案构建问题列表（自适应面试）
     */
    private List<InterviewQuestionDTO> buildQuestionsFromAnswers(String sessionId) {
        List<InterviewAnswerEntity> answers = persistenceService.findAnswersBySessionId(sessionId);
        return answers.stream()
            .map(answer -> InterviewQuestionDTO.create(
                answer.getQuestionIndex(),
                answer.getQuestion(),
                answer.getCategory() != null ? answer.getCategory() : "Java基础"
            ))
            .toList();
    }

    /**
     * 将缓存会话转换为 DTO（不包含问题列表）
     */
    private InterviewSessionBasicDTO toDTO(CachedSession session, int answeredCount) {
        // 总题数使用 totalQuestions（用户选择的），不是 questionsGenerated（已生成的）
        int totalQuestions = session.getTotalQuestions() != null ? session.getTotalQuestions() : 0;

        return new InterviewSessionBasicDTO(
                session.getSessionId(),
                session.getResumeText(),
                totalQuestions,
                session.getCurrentIndex(),
                session.getStatus().name(),
                "BASIC",
                null,
                totalQuestions,
                answeredCount
        );
    }

    /**
     * 切换面试知识库
     * 自适应面试：直接更新知识库ID，后续生成问题时使用新的知识库
     * 旧版面试：根据新的知识库重新生成所有未回答的问题
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

        // 判断是自适应面试还是旧版批量生成面试
        // 自适应面试：直接更新知识库ID到数据库，后续生成问题时使用新的知识库
        log.info("切换面试知识库: sessionId={}, 新知识库IDs={}, 当前进度={}",
                sessionId, knowledgeBaseIds, session.getCurrentIndex());

        persistenceService.updateKnowledgeBaseIds(sessionId, knowledgeBaseIds);

        // 更新本地 session 对象
        session.setKnowledgeBaseIds(knowledgeBaseIds);

        log.info("切换知识库完成: sessionId={}", sessionId);

        // 计算已答题数量
        int answeredCount = persistenceService.findAnswersBySessionId(sessionId).stream()
                .filter(a -> a.getUserAnswer() != null && !a.getUserAnswer().isBlank())
                .toList().size();

        return toDTO(session, answeredCount);
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
     * 获取当前问题（自适应难度版本 - 工作流模式）
     * 触发工作流执行：entry → question_generator → [中断]
     * 问题生成后通过 SSE 推送给前端
     *
     * @param sessionId 会话ID
     * @return 当前问题DTO
     */
    public CurrentQuestionDTO getCurrentQuestionForAdaptive(String sessionId) {
        // 获取会话实体
        InterviewSessionEntity session = persistenceService.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        // 检查会话状态，已结束的面试不能获取新问题
        if (session.getStatus() == InterviewSessionEntity.SessionStatus.COMPLETED ||
            session.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED, "面试已结束");
        }

        // 计算当前问题索引
        int questionIndex = session.getCurrentQuestionIndex() != null ? session.getCurrentQuestionIndex() : 0;

        // 判断是否已超出总题数
        if (session.getTotalQuestions() != null && questionIndex >= session.getTotalQuestions()) {
            return null; // 所有问题已回答完毕
        }

        // 检查是否有未完成的工作流，如果有则恢复
        if (workflowExecutor.hasActiveWorkflow(sessionId)) {
            log.info("发现未完成的工作流，恢复执行: sessionId={}", sessionId);
            // 获取当前状态
            var stateSnapshot = workflowExecutor.getWorkflowState(sessionId);
            if (stateSnapshot.isPresent()) {
                var state = stateSnapshot.get().state();
                return toCurrentQuestionDTO(state);
            }
        }

        // 触发工作流执行：entry → question_generator → [中断]
        // 工作流内部会通过 SSE 推送问题
        OverAllState state = workflowExecutor.executeToQuestionGenerator(sessionId);

        // 更新会话状态
        if (session.getStatus() == InterviewSessionEntity.SessionStatus.CREATED) {
            session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            persistenceService.updateSessionStatus(sessionId, InterviewSessionEntity.SessionStatus.IN_PROGRESS);
        }

        // 从工作流状态中提取问题信息并返回
        return toCurrentQuestionDTO(state);
    }

    /**
     * 将 OverAllState 转换为 CurrentQuestionDTO
     */
    private CurrentQuestionDTO toCurrentQuestionDTO(com.alibaba.cloud.ai.graph.OverAllState state) {
        Integer questionIndex = (Integer) state.value("currentQuestionIndex").orElse(0);
        String question = (String) state.value("currentQuestion").orElse("");
        String category = (String) state.value("currentCategory").orElse("");
        String difficulty = (String) state.value("currentDifficulty").orElse("BASIC");
        Long knowledgeBaseId = (Long) state.value("knowledgeBaseId").orElse(null);
        String knowledgeBaseName = (String) state.value("knowledgeBaseName").orElse(null);
        Long createdByPerspectiveId = (Long) state.value("createdByPerspectiveId").orElse(null);
        String createdByPerspectiveName = (String) state.value("createdByPerspectiveName").orElse(null);

        log.info("toCurrentQuestionDTO: perspectiveId={}, perspectiveName={}", createdByPerspectiveId, createdByPerspectiveName);

        return new CurrentQuestionDTO(
                questionIndex,
                question,
                category,
                difficulty,
                knowledgeBaseId,
                knowledgeBaseName,
                null, // referenceContext
                false, // isFollowUp
                null, // relatedIndex
                null, // relatedQuestion
                createdByPerspectiveId,
                createdByPerspectiveName
        );
    }

    /**
     * 获取会话进度和历史答题记录
     * 用于继续面试场景
     */
    public SessionProgressDTO getSessionProgress(String sessionId) {
        // 获取会话实体
        InterviewSessionEntity session = persistenceService.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        // 获取已回答的问题
        List<InterviewAnswerEntity> existingAnswers = persistenceService.findAnswersBySessionId(sessionId);

        // 构建历史答题记录（只包含已回答的）
        List<AnswerHistoryDTO> history = existingAnswers.stream()
                .filter(a -> a.getUserAnswer() != null && !a.getUserAnswer().isBlank())
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

        // 获取当前问题索引和总题数
        int currentIndex = session.getCurrentQuestionIndex() != null ? session.getCurrentQuestionIndex() : 0;
        int totalQuestions = session.getTotalQuestions() != null ? session.getTotalQuestions() : 0;

        // 从已有答案中找出当前问题
        InterviewAnswerEntity currentAnswerEntity = existingAnswers.stream()
                .filter(a -> a.getQuestionIndex() == currentIndex)
                .findFirst()
                .orElse(null);

        // 判断处理状态
        SessionProgressDTO.ProcessingStatus processingStatus = SessionProgressDTO.ProcessingStatus.IDLE;
        CurrentQuestionDTO currentQuestion = null;

        if (currentAnswerEntity != null) {
            if (currentAnswerEntity.getUserAnswer() == null || currentAnswerEntity.getUserAnswer().isBlank()) {
                // 题目已生成，用户未答
                currentQuestion = getCurrentQuestionDTO(currentAnswerEntity);
            } else if (currentAnswerEntity.getScore() == null || currentAnswerEntity.getScore() == 0) {
                // 用户已提交答案，工作流正在处理中（score=0 表示尚未评分）
                processingStatus = SessionProgressDTO.ProcessingStatus.PROCESSING;
            } else {
                // 已评分，返回当前问题（前端应该显示下一题）
                currentQuestion = getCurrentQuestionDTO(currentAnswerEntity);
            }
        }

        log.info("获取会话进度: sessionId={}, currentIndex={}, total={}, historySize={}, processingStatus={}",
                sessionId, currentIndex, totalQuestions, history.size(), processingStatus);

        return new SessionProgressDTO(
                sessionId,
                currentIndex,
                totalQuestions,
                currentQuestion,
                history,
                processingStatus
        );
    }

    private CurrentQuestionDTO getCurrentQuestionDTO(InterviewAnswerEntity currentAnswerEntity) {
        CurrentQuestionDTO currentQuestion = null;
        if (currentAnswerEntity != null) {
            currentQuestion = new CurrentQuestionDTO(
                    currentAnswerEntity.getQuestionIndex(),
                    currentAnswerEntity.getQuestion(),
                    currentAnswerEntity.getCategory(),
                    currentAnswerEntity.getDifficulty(),
                    currentAnswerEntity.getKnowledgeBaseId(),
                    null, // knowledgeBaseName
                    currentAnswerEntity.getReferenceContext(),
                    currentAnswerEntity.getIsFollowUp(),
                    currentAnswerEntity.getRelatedIndex(),
                    currentAnswerEntity.getRelatedQuestion(),
                    currentAnswerEntity.getCreatedByPerspectiveId(),
                    currentAnswerEntity.getCreatedByPerspectiveName()
            );
        }
        return currentQuestion;
    }

    /**
     * 提交答案（自适应难度版本）
     * 保存答案 -> AI评估 -> 更新分类得分 -> 调整难度 -> 返回下一题
     */
    public SubmitAnswerResponse submitAnswerForAdaptive(String sessionId, Integer questionIndex, String answer) {
        // 获取会话实体
        InterviewSessionEntity session = persistenceService.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        // 检查会话状态，防止对已完成的面试提交答案
        if (session.getStatus() == InterviewSessionEntity.SessionStatus.COMPLETED ||
            session.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED, "面试已结束，无法提交答案");
        }

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

        // 调整难度（根据该视角的答题表现）
        String adjustedDifficulty = difficultyAdjustmentService.adjustDifficulty(
                currentAnswer.getDifficulty(), evaluationResult.score());

        // 保存答案（含评估结果和调整后的难度）
        persistenceService.saveAnswerWithDifficulty(
                sessionId,
                questionIndex,
                currentAnswer.getQuestion(),
                currentAnswer.getCategory(),
                answer,
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

        // 更新问题索引
        int newIndex = questionIndex + 1;
        boolean hasNextQuestion = session.getTotalQuestions() == null || newIndex < session.getTotalQuestions();

        // 更新会话状态
        persistenceService.updateCurrentQuestionIndex(sessionId, newIndex);

        CurrentQuestionDTO nextQuestion = null;
        if (hasNextQuestion) {
            // 生成下一题
            session.setCurrentQuestionIndex(newIndex);

            // 多视角模式：轮询选择出题视角
            Long nextPerspectiveId = null;
            String nextPerspectiveName = null;
            String nextPerspectivePrompt = null;
            if (session.getSelectedPerspectives() != null && !session.getSelectedPerspectives().isBlank()) {
                try {
                    List<Long> selectedPerspectives = objectMapper.readValue(
                            session.getSelectedPerspectives(), new TypeReference<>() {
                            });
                    // 解析会话级权重配置
                    Map<Long, Double> sessionWeights = null;
                    if (session.getPerspectiveWeights() != null && !session.getPerspectiveWeights().isBlank()) {
                        sessionWeights = objectMapper.readValue(
                                session.getPerspectiveWeights(), new TypeReference<>() {
                                });
                    }
                    if (selectedPerspectives != null && !selectedPerspectives.isEmpty()) {
                        nextPerspectiveId = perspectiveEvaluationService.selectNextQuestionPerspective(
                                selectedPerspectives, session.getLastQuestionPerspectiveId(), sessionWeights);
                        InterviewerRoleEntity role = interviewerRoleRepository.findById(nextPerspectiveId).orElse(null);
                        if (role != null) {
                            nextPerspectiveName = role.getRoleName();
                            nextPerspectivePrompt = role.getScoringPrompt();
                        }
                        // 更新会话的上一题视角ID
                        session.setLastQuestionPerspectiveId(nextPerspectiveId);
                        persistenceService.updateLastQuestionPerspectiveId(sessionId, nextPerspectiveId);

                        // 获取该视角下的最新难度（按视角隔离，不再使用 session.getCurrentDifficulty()）
                        Optional<InterviewAnswerEntity> lastAnswerForNextPerspective =
                                persistenceService.findLastAnswerBySessionAndPerspective(session.getId(), nextPerspectiveId);
                        if (lastAnswerForNextPerspective.isPresent()) {
                            session.setCurrentDifficulty(lastAnswerForNextPerspective.get().getDifficulty());
                        } else {
                            // 该视角第一次出题，使用默认难度
                            session.setCurrentDifficulty("BASIC");
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析selectedPerspectives失败: {}", e.getMessage());
                }
            }

            // 构建历史答题记录（包含已回答的问题和答案）
            List<AnswerHistoryDTO> history = existingAnswers.stream()
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

            // AI会根据简历和历史自行决定问题内容和分类，使用视角prompt
            nextQuestion = questionGenerationService.generateSingleQuestion(
                    session, newIndex, resumeText, history,
                    nextPerspectiveId, nextPerspectivePrompt, nextPerspectiveName,
                    null);

            // 保存下一题到数据库（含视角信息）
            // AI返回的relatedIndex已经是全局questionIndex，无需转换
            Integer globalRelatedIndex = getGlobalRelatedIndex(sessionId, nextQuestion, newIndex, nextPerspectiveId, nextPerspectiveName);

            // 构建包含视角信息的nextQuestionDTO
            final Long finalPerspectiveId = nextPerspectiveId;
            final String finalPerspectiveName = nextPerspectiveName;
            nextQuestion = new CurrentQuestionDTO(
                    nextQuestion.questionIndex(),
                    nextQuestion.question(),
                    nextQuestion.category(),
                    nextQuestion.difficulty(),
                    nextQuestion.knowledgeBaseId(),
                    nextQuestion.knowledgeBaseName(),
                    nextQuestion.referenceContext(),
                    nextQuestion.isFollowUp(),
                    globalRelatedIndex,
                    nextQuestion.relatedQuestion(),
                    finalPerspectiveId,
                    finalPerspectiveName
            );

            // 确保状态保持 IN_PROGRESS
            if (session.getStatus() != InterviewSessionEntity.SessionStatus.IN_PROGRESS) {
                session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
                persistenceService.updateSessionStatus(sessionId, InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            }
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

        log.info("提交答案完成: sessionId={}, questionIndex={}, score={}, adjustedDifficulty={}, hasNext={}",
                sessionId, questionIndex, evaluationResult.score(), adjustedDifficulty, hasNextQuestion);

        return new SubmitAnswerResponse(
                hasNextQuestion,
                nextQuestion,
                newIndex,
                generated,
                currentScore,
                categoryScores,
                adjustedDifficulty
        );
    }

    @Nullable
    private Integer getGlobalRelatedIndex(String sessionId, CurrentQuestionDTO nextQuestion, int newIndex, Long nextPerspectiveId, String nextPerspectiveName) {
        Integer globalRelatedIndex = null;
        if (Boolean.TRUE.equals(nextQuestion.isFollowUp()) && nextQuestion.relatedIndex() != null) {
            globalRelatedIndex = nextQuestion.relatedIndex();
        }
        persistenceService.saveAnswerWithDifficulty(
                sessionId,
                newIndex,
                nextQuestion.question(),
                nextQuestion.category(),
                null, // userAnswer为null，等待用户回答后更新
                nextQuestion.difficulty(),
                nextQuestion.knowledgeBaseId(),
                nextQuestion.referenceContext(),
                0,  // 初始评分为0
                null, // 初始反馈为null
                nextPerspectiveId,
                nextPerspectiveName,
                nextQuestion.isFollowUp(),
                globalRelatedIndex,
                nextQuestion.relatedQuestion(),
                null,
                null
        );
        return globalRelatedIndex;
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

    /**
     * 保存答案并恢复工作流执行（工作流模式）
     * 恢复工作流：scorer → decider → [ASK] question_generator → [中断]
     *                   → [SWITCH] role_switcher → question_generator → [中断]
     *                   → [FINISH] final_reporter → [完成]
     * 问题生成后通过 SSE 推送给前端
     *
     * @param sessionId 会话ID
     * @param questionIndex 问题索引
     * @param answer 用户答案
     * @return 提交答案响应（包含下一题信息，但实际下一题通过 SSE 推送）
     */
    public SubmitAnswerResponse saveAnswerAndTriggerWorkflow(String sessionId, Integer questionIndex, String answer) {
        // 获取会话实体
        InterviewSessionEntity session = persistenceService.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        // 检查会话状态
        if (session.getStatus() == InterviewSessionEntity.SessionStatus.COMPLETED ||
            session.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED) {
            throw new BusinessException(ErrorCode.INTERVIEW_ALREADY_COMPLETED, "面试已结束，无法提交答案");
        }

        // 获取当前问题
        List<InterviewAnswerEntity> existingAnswers = persistenceService.findAnswersBySessionId(sessionId);
        InterviewAnswerEntity currentAnswer = existingAnswers.stream()
                .filter(a -> a.getQuestionIndex().equals(questionIndex))
                .findFirst()
                .orElse(null);

        if (currentAnswer == null) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_NOT_FOUND, "问题不存在");
        }

        // 保存用户答案（不含评分，等待工作流评分）
        persistenceService.saveAnswerWithDifficulty(
                sessionId,
                questionIndex,
                currentAnswer.getQuestion(),
                currentAnswer.getCategory(),
                answer,
                currentAnswer.getDifficulty(),
                currentAnswer.getKnowledgeBaseId(),
                currentAnswer.getReferenceContext(),
                0,  // 初始评分为0，等待工作流更新
                null  // 初始反馈为null，等待工作流更新
        );

        log.info("答案已保存，启动异步工作流执行: sessionId={}, questionIndex={}", sessionId, questionIndex);

        // 异步恢复工作流执行：scorer → decider → [分支]
        // 工作流内部会通过 SSE 推送下一题或完成事件
        workflowExecutor.resumeAsync(sessionId, questionIndex, answer);

        // 立即返回成功响应，实际结果通过 SSE 推送
        int newIndex = questionIndex + 1;
        return new SubmitAnswerResponse(
                true, // hasNextQuestion - SSE 会推送下一题
                null, // nextQuestion - 通过 SSE 推送
                newIndex,
                session.getQuestionsGenerated() != null ? session.getQuestionsGenerated() : 0,
                0, // currentScore - 稍后从数据库获取
                Map.of(), // categoryScores - 稍后从数据库获取
                null // nextDifficulty
        );
    }

}

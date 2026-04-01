package interview.guide.modules.interview;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.interview.model.*;
import interview.guide.modules.interview.service.InterviewHistoryService;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.interview.service.InterviewSessionService;
import interview.guide.modules.interview.service.PerspectiveEvaluationService;
import interview.guide.modules.interview.service.ScoreTrendService;
import interview.guide.modules.interview.workflow.WorkflowExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 面试控制器
 * 提供模拟面试相关的API接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InterviewController {
    
    private final InterviewSessionService sessionService;
    private final InterviewHistoryService historyService;
    private final InterviewPersistenceService persistenceService;
    private final ScoreTrendService scoreTrendService;
    private final PerspectiveEvaluationService perspectiveEvaluationService;
    private final WorkflowExecutor workflowExecutor;
    
    /**
     * 创建面试会话
     */
    @PostMapping("/api/interview/sessions")
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 5)
    public Result<InterviewSessionBasicDTO> createSession(
            @CurrentUser Long userId,
            @RequestBody CreateInterviewRequest request) {
        log.info("创建面试会话，用户: {}, 题目数量: {}", userId, request.questionCount());
        InterviewSessionBasicDTO session = sessionService.createSession(userId, request);
        return Result.success(session);
    }

    /**
     * 获取用户的所有面试会话列表
     * @param status 状态筛选 (pending/in_progress/completed)
     */
    @GetMapping("/api/interview/sessions")
    public Result<List<InterviewSessionListItemDTO>> getAllSessions(
            @CurrentUser Long userId,
            @RequestParam(required = false) String status) {
        List<InterviewSessionListItemDTO> sessions = historyService.getAllSessions(userId, status);
        return Result.success(sessions);
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/api/interview/sessions/{sessionId}")
    public Result<InterviewSessionBasicDTO> getSession(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        InterviewSessionBasicDTO session = sessionService.getSession(userId, sessionId);
        return Result.success(session);
    }
    
    /**
     * 获取当前问题（返回完整字段：difficulty, knowledgeBaseId, knowledgeBaseName, referenceContext）
     */
    @GetMapping("/api/interview/sessions/{sessionId}/question")
    public Result<CurrentQuestionDTO> getCurrentQuestion(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        log.info("获取当前问题: 用户{}, 会话{}", userId, sessionId);
        sessionService.validateSessionOwnership(userId, sessionId);
        CurrentQuestionDTO question = sessionService.getCurrentQuestionForAdaptive(sessionId);
        return Result.success(question);
    }

    /**
     * 提交答案（自适应难度版本）
     */
    @PostMapping("/api/interview/sessions/{sessionId}/answers")
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL}, count = 10)
    public Result<SubmitAnswerResponse> submitAnswer(
            @CurrentUser Long userId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        sessionService.validateSessionOwnership(userId, sessionId);
        Integer questionIndex = (Integer) body.get("questionIndex");
        String answer = (String) body.get("answer");
        log.info("提交答案: 用户{}, 会话{}, 问题{}", userId, sessionId, questionIndex);
        SubmitAnswerResponse response = sessionService.submitAnswerForAdaptive(sessionId, questionIndex, answer);
        return Result.success(response);
    }

    /**
     * 查询面试评估状态（轻量级接口，不触发评估）
     */
    @GetMapping("/api/interview/sessions/{sessionId}/status")
    public Result<InterviewEvaluateStatusDTO> getEvaluateStatus(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        sessionService.validateSessionOwnership(userId, sessionId);
        InterviewEvaluateStatusDTO status = sessionService.getEvaluateStatus(sessionId);
        return Result.success(status);
    }

    /**
     * 生成面试报告
     */
    @GetMapping("/api/interview/sessions/{sessionId}/report")
    public Result<InterviewReportDTO> getReport(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        log.info("生成面试报告: 用户{}, 会话{}", userId, sessionId);
        sessionService.validateSessionOwnership(userId, sessionId);
        InterviewReportDTO report = sessionService.generateReport(sessionId);
        return Result.success(report);
    }
    
    /**
     * 查找未完成的面试会话
     * GET /api/interview/sessions/unfinished/{resumeId}
     */
    @GetMapping("/api/interview/sessions/unfinished/{resumeId}")
    public Result<InterviewSessionBasicDTO> findUnfinishedSession(
            @CurrentUser Long userId,
            @PathVariable Long resumeId) {
        return Result.success(sessionService.findUnfinishedSessionOrThrow(userId, resumeId));
    }

    /**
     * 提前交卷
     */
    @PostMapping("/api/interview/sessions/{sessionId}/complete")
    public Result<Void> completeInterview(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        sessionService.validateSessionOwnership(userId, sessionId);
        log.info("提前交卷: 用户{}, 会话{}", userId, sessionId);
        sessionService.completeInterview(sessionId);
        return Result.success(null);
    }
    
    /**
     * 获取面试会话详情
     * GET /api/interview/sessions/{sessionId}/details
     */
    @GetMapping("/api/interview/sessions/{sessionId}/details")
    public Result<InterviewDetailDTO> getInterviewDetail(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        sessionService.validateSessionOwnership(userId, sessionId);
        InterviewDetailDTO detail = historyService.getInterviewDetail(sessionId);
        return Result.success(detail);
    }
    
    /**
     * 导出面试报告为PDF
     */
    @GetMapping("/api/interview/sessions/{sessionId}/export")
    public ResponseEntity<byte[]> exportInterviewPdf(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        sessionService.validateSessionOwnership(userId, sessionId);
        try {
            byte[] pdfBytes = historyService.exportInterviewPdf(sessionId);
            String filename = URLEncoder.encode("模拟面试报告_" + sessionId + ".pdf",
                StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (Exception e) {
            log.error("导出PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除面试会话
     */
    @DeleteMapping("/api/interview/sessions/{sessionId}")
    public Result<Void> deleteInterview(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        sessionService.validateSessionOwnership(userId, sessionId);
        log.info("删除面试会话: 用户{}, 会话{}", userId, sessionId);
        persistenceService.deleteSessionBySessionId(sessionId);
        return Result.success(null);
    }

    /**
     * 切换面试知识库
     * 会根据新的知识库重新生成未回答的问题
     */
    @PutMapping("/api/interview/sessions/{sessionId}/knowledge-base")
    public Result<InterviewSessionBasicDTO> switchKnowledgeBase(
            @CurrentUser Long userId,
            @PathVariable String sessionId,
            @RequestBody SwitchKnowledgeBaseRequest request) {
        log.info("切换面试知识库: 用户{}, 会话{}, 知识库IDs={}", userId, sessionId, request.knowledgeBaseIds());
        sessionService.validateSessionOwnership(userId, sessionId);
        InterviewSessionBasicDTO session = sessionService.switchKnowledgeBase(userId, sessionId, request.knowledgeBaseIds());
        return Result.success(session);
    }

    /**
     * 获取评分趋势
     * GET /api/interview/score-trend
     */
    @GetMapping("/api/interview/score-trend")
    public Result<interview.guide.modules.interview.model.ScoreTrendDTO> getScoreTrend(
            @CurrentUser Long userId) {
        log.info("获取评分趋势: 用户{}", userId);
        interview.guide.modules.interview.model.ScoreTrendDTO trend = scoreTrendService.getScoreTrend(userId);
        return Result.success(trend);
    }

    /**
     * 获取当前问题（自适应难度版本）
     * GET /api/interview/sessions/{sessionId}/current
     */
    @GetMapping("/api/interview/sessions/{sessionId}/current")
    public Result<CurrentQuestionDTO> getCurrentQuestionAdaptive(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        log.info("获取当前问题: 用户{}, 会话{}", userId, sessionId);
        sessionService.validateSessionOwnership(userId, sessionId);
        CurrentQuestionDTO question = sessionService.getCurrentQuestionForAdaptive(sessionId);
        return Result.success(question);
    }

    /**
     * 获取会话进度和历史答题记录
     * GET /api/interview/sessions/{sessionId}/progress
     */
    @GetMapping("/api/interview/sessions/{sessionId}/progress")
    public Result<SessionProgressDTO> getSessionProgress(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        log.info("获取会话进度: 用户{}, 会话{}", userId, sessionId);
        sessionService.validateSessionOwnership(userId, sessionId);
        SessionProgressDTO progress = sessionService.getSessionProgress(sessionId);
        return Result.success(progress);
    }

    /**
     * 提交答案（触发工作流版本）
     * POST /api/interview/sessions/{sessionId}/answer
     *
     * 立即返回结果，后台通过 SSE 推送工作流执行进度
     */
    @PostMapping("/api/interview/sessions/{sessionId}/answer")
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL}, count = 10)
    public Result<Void> submitAnswerAdaptive(
            @CurrentUser Long userId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        sessionService.validateSessionOwnership(userId, sessionId);
        Integer questionIndex = (Integer) body.get("questionIndex");
        String answer = (String) body.get("answer");
        log.info("提交答案: 用户{}, 会话{}, 问题{}", userId, sessionId, questionIndex);

        // 保存答案并触发工作流（异步执行）
        sessionService.saveAnswerAndTriggerWorkflow(sessionId, questionIndex, answer);

        // 立即返回，后台工作流通过 SSE 推送结果
        return Result.success(null);
    }

    /**
     * 获取能力画像
     * GET /api/interview/sessions/{sessionId}/profile
     */
    @GetMapping("/api/interview/sessions/{sessionId}/profile")
    public Result<AbilityProfileDTO> getAbilityProfile(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        log.info("获取能力画像: 用户{}, 会话{}", userId, sessionId);
        sessionService.validateSessionOwnership(userId, sessionId);
        AbilityProfileDTO profile = sessionService.getAbilityProfile(sessionId);
        return Result.success(profile);
    }

    // ==================== 多视角面试相关接口 ====================

    /**
     * 获取各视角评分状态和结果
     * GET /api/interview/sessions/{sessionId}/perspectives
     */
    @GetMapping("/api/interview/sessions/{sessionId}/perspectives")
    public Result<List<PerspectiveScoreDTO>> getPerspectiveScores(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        log.info("获取视角评分列表: 用户{}, 会话{}", userId, sessionId);
        sessionService.validateSessionOwnership(userId, sessionId);
        List<PerspectiveScoreDTO> scores = perspectiveEvaluationService.getPerspectiveScores(
                persistenceService.findBySessionId(sessionId).orElseThrow().getId());
        return Result.success(scores);
    }

    /**
     * 获取指定视角详情
     * GET /api/interview/sessions/{sessionId}/perspectives/{perspectiveId}
     */
    @GetMapping("/api/interview/sessions/{sessionId}/perspectives/{perspectiveId}")
    public Result<PerspectiveDetailDTO> getPerspectiveDetail(
            @CurrentUser Long userId,
            @PathVariable String sessionId,
            @PathVariable Long perspectiveId) {
        log.info("获取视角详情: 用户{}, 会话{}, 视角{}", userId, sessionId, perspectiveId);
        sessionService.validateSessionOwnership(userId, sessionId);
        Long sessionIdLong = persistenceService.findBySessionId(sessionId).orElseThrow().getId();
        PerspectiveDetailDTO detail = perspectiveEvaluationService.getPerspectiveDetail(sessionIdLong, perspectiveId);
        return Result.success(detail);
    }

    /**
     * 获取综合报告
     * GET /api/interview/sessions/{sessionId}/report/comprehensive
     */
    @GetMapping("/api/interview/sessions/{sessionId}/report/comprehensive")
    public Result<ComprehensiveReportDTO> getComprehensiveReport(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {
        log.info("获取综合报告: 用户{}, 会话{}", userId, sessionId);
        sessionService.validateSessionOwnership(userId, sessionId);
        Long sessionIdLong = persistenceService.findBySessionId(sessionId).orElseThrow().getId();
        ComprehensiveReportDTO report = perspectiveEvaluationService.getComprehensiveReportFromDb(sessionIdLong);
        return Result.success(report);
    }
}

package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.interview.model.*;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.interview.repository.InterviewerRoleRepository;
import interview.guide.modules.interview.repository.PerspectiveScoreRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多视角评估服务
 * 处理多视角批量并行评分、轮询出题、智能汇总报告生成
 */
@Slf4j
@Service
public class PerspectiveEvaluationService {

    private final PerspectiveScoreRepository perspectiveScoreRepository;
    private final InterviewerRoleRepository interviewerRoleRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final InterviewAnswerRepository interviewAnswerRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/perspective-summary.st")
    private Resource summaryPromptResource;

    // ========== 自注入（解决同类调用 @Async 不生效的问题）==========
    private final PerspectiveEvaluationService self;

    @Autowired
    public PerspectiveEvaluationService(
            PerspectiveScoreRepository perspectiveScoreRepository,
            InterviewerRoleRepository interviewerRoleRepository,
            InterviewSessionRepository interviewSessionRepository,
            InterviewAnswerRepository interviewAnswerRepository,
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            ObjectMapper objectMapper,
            @Lazy PerspectiveEvaluationService self) {
        this.perspectiveScoreRepository = perspectiveScoreRepository;
        this.interviewerRoleRepository = interviewerRoleRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.interviewAnswerRepository = interviewAnswerRepository;
        this.chatClientBuilder = chatClientBuilder;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    // ========== 评估报告结果（批量评估时 AI 返回的中间 DTO）==========
    private record EvaluationReportDTO(
        int overallScore,
        String overallFeedback,
        List<String> strengths,
        List<String> improvements,
        List<PerspectiveQuestionEvalDTO> questionEvaluations
    ) {}

    // ========== 综合反馈结构化输出 ==========
    private record ComprehensiveFeedbackDTO(
        String evaluation,
        String developmentSuggestions
    ) {}

    private record PerspectiveQuestionEvalDTO(
        int questionIndex,
        String question,
        int score,
        String feedback,
        String userAnswer,
        String referenceAnswer,
        List<String> keyPoints
    ) {}

    /**
     * 面试结束后的多视角评估汇总（同步，供消息队列消费者调用）
     * 流程：按视角批量并行评估 → 等待完成 → 生成综合报告
     */
    @Transactional(rollbackFor = Exception.class)
    public ComprehensiveReportDTO evaluateAndSummarize(String sessionId) {
        log.info("多视角评估汇总开始: sessionId={}", sessionId);

        // 获取会话
        Optional<InterviewSessionEntity> sessionOpt = interviewSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        InterviewSessionEntity session = sessionOpt.get();

        // 获取选择的视角列表
        List<Long> selectedPerspectives = parsePerspectiveIds(session.getSelectedPerspectives());
        if (selectedPerspectives == null || selectedPerspectives.isEmpty()) {
            log.info("会话无多视角配置，跳过多视角评估: sessionId={}", sessionId);
            return null;
        }

        // 获取会话的数据库ID
        Long dbSessionId = session.getId();

        // Step 1: 按视角分组，批量并行调用评估（每个视角一次 AI 调用，不是逐题）
        log.info("开始按视角批量并行评估: sessionId={}, perspectiveCount={}", sessionId, selectedPerspectives.size());

        // 通过自注入的代理调用 @Async 方法，确保异步生效
        List<CompletableFuture<PerspectiveBatchResult>> futures = new ArrayList<>();
        for (Long perspectiveId : selectedPerspectives) {
            CompletableFuture<PerspectiveBatchResult> future = self.evaluatePerspectiveBatchAsync(dbSessionId, perspectiveId);
            futures.add(future);
        }

        // 等待所有视角批量评估完成，并保存结果
        List<PerspectiveBatchResult> results = new ArrayList<>();
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<PerspectiveBatchResult> f : futures) {
                    try {
                        PerspectiveBatchResult r = f.get();
                        if (r != null) {
                            results.add(r);
                            // 保存视角综合评分到数据库（在同步事务中执行）
                            savePerspectiveScoreFromResult(dbSessionId, r);
                        }
                    } catch (Exception e) {
                        log.error("获取视角评估结果时发生异常: {}", e.getMessage());
                    }
                }
                log.info("所有视角批量评估已完成: sessionId={}, 成功={}", sessionId, results.size());
            } catch (Exception e) {
                log.error("等待视角批量评估完成时发生异常: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }

        // Step 2: 生成综合报告（各视角评估都完成后才执行）
        log.info("开始生成综合报告: sessionId={}", sessionId);
        ComprehensiveReportDTO report = generateComprehensiveReport(sessionId, results);

        if (report == null) {
            log.warn("综合报告生成失败（所有视角评估均失败）: sessionId={}", sessionId);
        }
        log.info("多视角评估汇总完成: sessionId={}, score={}", sessionId, report != null ? report.overallScore() : 0);
        return report;
    }

    /**
     * 解析视角ID列表
     */
    private List<Long> parsePerspectiveIds(String selectedPerspectivesJson) {
        if (selectedPerspectivesJson == null || selectedPerspectivesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(selectedPerspectivesJson, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("解析视角ID列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析会话级视角权重配置
     */
    private Map<Long, Double> parsePerspectiveWeights(String perspectiveWeightsJson) {
        if (perspectiveWeightsJson == null || perspectiveWeightsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(perspectiveWeightsJson, new TypeReference<Map<Long, Double>>() {});
        } catch (Exception e) {
            log.warn("解析视角权重配置失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 按视角批量并行评估（纯 AI 调用，异步执行，不做数据库操作）
     * 答案在提交时已有 AI 评估结果（score, feedback, referenceAnswer），直接复用，
     * 按视角分组后批量调用 AI 生成视角综合报告。
     */
    @Async
    public CompletableFuture<PerspectiveBatchResult> evaluatePerspectiveBatchAsync(Long sessionId, Long perspectiveId) {
        log.info("视角批量评估开始: sessionId={}, perspectiveId={}", sessionId, perspectiveId);

        try {
            // 获取视角信息
            InterviewerRoleEntity role = interviewerRoleRepository.findById(perspectiveId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "视角不存在"));

            // 获取会话信息（同时加载简历，避免懒加载问题）
            InterviewSessionEntity session = interviewSessionRepository.findByIdWithResume(sessionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

            // 获取该视角创建的所有答案（隐私隔离）
            List<InterviewAnswerEntity> perspectiveAnswers =
                    interviewAnswerRepository.findBySessionIdAndCreatedByPerspectiveIdOrderByQuestionIndexAsc(sessionId, perspectiveId);

            if (perspectiveAnswers.isEmpty()) {
                log.warn("视角 {} 无题目，跳过: sessionId={}", perspectiveId, sessionId);
                return CompletableFuture.completedFuture(null);
            }

            // 构建批量评估的问题列表（使用已有评估数据）
            List<PerspectiveQuestionEvalDTO> questionEvals = new ArrayList<>();
            for (InterviewAnswerEntity answer : perspectiveAnswers) {
                if (answer.getQuestionIndex() < 0) continue; // 跳过综合评分记录

                String referenceAnswer = answer.getReferenceAnswer() != null ? answer.getReferenceAnswer() : "";
                List<String> keyPoints = parseJsonList(answer.getKeyPointsJson());

                questionEvals.add(new PerspectiveQuestionEvalDTO(
                        answer.getQuestionIndex(),
                        answer.getQuestion(),
                        answer.getScore() != null ? answer.getScore() : 0,
                        answer.getFeedback() != null ? answer.getFeedback() : "",
                        answer.getUserAnswer(),
                        referenceAnswer,
                        keyPoints
                ));
            }

            // 调用 AI 生成视角综合报告
            EvaluationReportDTO report = callAIBatchEvaluation(role, session, questionEvals);

            log.info("视角 AI 评估完成: sessionId={}, perspectiveId={}, roleName={}, score={}",
                    sessionId, perspectiveId, role.getRoleName(), report.overallScore());

            return CompletableFuture.completedFuture(new PerspectiveBatchResult(
                    role.getId(),
                    role.getRoleName(),
                    role.getRoleCode(),
                    role.getIcon(),
                    role.getWeight() != null ? role.getWeight() : 1.0,
                    report.overallScore(),
                    report.overallFeedback(),
                    report.strengths(),
                    report.improvements(),
                    questionEvals
            ));

        } catch (Exception e) {
            log.error("视角批量评估异常: sessionId={}, perspectiveId={}, error={}", sessionId, perspectiveId, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 构建批量评估的 Q&A 记录字符串
     */
    private String buildQARecordsForBatch(List<PerspectiveQuestionEvalDTO> questions) {
        StringBuilder sb = new StringBuilder();
        for (PerspectiveQuestionEvalDTO q : questions) {
            sb.append(String.format("【问题%d】\n", q.questionIndex() + 1));
            sb.append(String.format("题目: %s\n", q.question()));
            sb.append(String.format("得分: %d分\n", q.score()));
            sb.append(String.format("AI评价: %s\n", q.feedback()));
            if (q.referenceAnswer() != null && !q.referenceAnswer().isBlank()) {
                sb.append(String.format("参考答案: %s\n", q.referenceAnswer()));
            }
            if (q.keyPoints() != null && !q.keyPoints().isEmpty()) {
                sb.append(String.format("核心要点: %s\n", String.join("、", q.keyPoints())));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 调用 AI 进行批量评估（生成视角综合报告）
     */
    private EvaluationReportDTO callAIBatchEvaluation(InterviewerRoleEntity role,
                                                    InterviewSessionEntity session,
                                                    List<PerspectiveQuestionEvalDTO> questions) {
        String basePrompt = role.getScoringPrompt();
        String resumeText = session.getResume() != null ? session.getResume().getResumeText() : "无简历内容";
        String qaRecords = buildQARecordsForBatch(questions);

        String systemPrompt = basePrompt + """

            请根据候选人的简历和以下问答记录，对该视角下候选人的整体表现进行综合评估。

            候选人简历：
            """ + resumeText + """

            问答记录：
            """ + qaRecords + """

            请以JSON格式返回评估报告：
            {"overallScore": 综合评分-0-100的整数, "overallFeedback": 综合评价内容, "strengths": ["优势1", "优势2"], "improvements": ["改进建议1", "改进建议2"]}
            """;

        try {
            ChatClient chatClient = chatClientBuilder.build();
            return structuredOutputInvoker.invoke(
                    chatClient,
                    systemPrompt,
                    "请根据上述信息生成视角综合评估报告。",
                    new BeanOutputConverter<>(EvaluationReportDTO.class),
                    interview.guide.common.exception.ErrorCode.AI_SERVICE_ERROR,
                    "视角批量评估AI调用失败：",
                    "PerspectiveBatchEvaluation",
                    log
            );
        } catch (Exception e) {
            log.warn("AI批量评估解析失败，使用默认评分: {}", e.getMessage());
            // 从已有答案计算默认分
            int avgScore = (int) questions.stream()
                    .mapToInt(q -> q.score())
                    .average()
                    .orElse(60.0);
            return new EvaluationReportDTO(avgScore, "评估服务暂时不可用，请稍后重试。", List.of(), List.of(), List.of());
        }
    }

    /**
     * 保存视角综合评分到数据库（questionIndex = -1）
     */
    private void savePerspectiveComprehensiveScore(Long sessionId, InterviewerRoleEntity role, EvaluationReportDTO report) {
        PerspectiveScoreEntity existing = perspectiveScoreRepository
                .findComprehensiveScoreBySessionIdAndPerspectiveId(sessionId, role.getId())
                .orElse(null);

        if (existing == null) {
            existing = new PerspectiveScoreEntity();
            existing.setSessionId(sessionId);
            existing.setPerspective(role);
            existing.setQuestionIndex(-1);
            existing.setStatus(PerspectiveScoreEntity.PerspectiveScoreStatus.PROCESSING);
        }

        existing.setScore(report.overallScore());
        existing.setFeedback(report.overallFeedback());
        existing.setStrengthsJson(toJson(report.strengths()));
        existing.setImprovementsJson(toJson(report.improvements()));
        existing.setStatus(PerspectiveScoreEntity.PerspectiveScoreStatus.COMPLETED);
        existing.setCompletedAt(LocalDateTime.now());
        perspectiveScoreRepository.save(existing);
    }

    /**
     * 保存视角综合评分（从 PerspectiveBatchResult 构建 EvaluationReportDTO）
     */
    private void savePerspectiveScoreFromResult(Long sessionId, PerspectiveBatchResult result) {
        InterviewerRoleEntity role = interviewerRoleRepository.findById(result.perspectiveId()).orElse(null);
        if (role == null) {
            log.warn("视角 {} 不存在，跳过保存: sessionId={}", result.perspectiveId(), sessionId);
            return;
        }
        EvaluationReportDTO report = new EvaluationReportDTO(
                result.score(),
                result.feedback(),
                result.strengths(),
                result.improvements(),
                List.of()
        );
        savePerspectiveComprehensiveScore(sessionId, role, report);
    }

    // ========== 批量评估结果 ==========
    private record PerspectiveBatchResult(
        Long perspectiveId,
        String perspectiveName,
        String perspectiveCode,
        String icon,
        double weight,
        int score,
        String feedback,
        List<String> strengths,
        List<String> improvements,
        List<PerspectiveQuestionEvalDTO> questionEvals
    ) {}

    // ========== 选择下一个问题的视角（轮询算法）==========
    /**
     * 选择下一个问题的视角
     * @param selectedPerspectives 已选择的视角列表
     * @param lastPerspectiveId 上一个出题的视角ID
     * @param sessionWeights 会话级权重配置（如果为null或空，则使用角色表中的默认权重）
     */
    public Long selectNextQuestionPerspective(List<Long> selectedPerspectives, Long lastPerspectiveId,
                                              Map<Long, Double> sessionWeights) {
        if (selectedPerspectives == null || selectedPerspectives.isEmpty()) {
            return null;
        }

        if (selectedPerspectives.size() == 1) {
            return selectedPerspectives.get(0);
        }

        List<Long> candidates = selectedPerspectives.stream()
                .filter(p -> !p.equals(lastPerspectiveId))
                .collect(Collectors.toList());

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        List<InterviewerRoleEntity> roles = interviewerRoleRepository.findAllById(candidates);
        // 优先使用会话级权重，否则使用角色表中的默认权重
        roles.sort((r1, r2) -> {
            double w1 = sessionWeights != null && sessionWeights.containsKey(r1.getId())
                    ? sessionWeights.get(r1.getId()) : (r1.getWeight() != null ? r1.getWeight() : 1.0);
            double w2 = sessionWeights != null && sessionWeights.containsKey(r2.getId())
                    ? sessionWeights.get(r2.getId()) : (r2.getWeight() != null ? r2.getWeight() : 1.0);
            return Double.compare(w2, w1); // 降序排列
        });
        return roles.get(0).getId();
    }

    // ========== 生成综合报告 ==========
    @Transactional(rollbackFor = Exception.class)
    public ComprehensiveReportDTO generateComprehensiveReport(String sessionId, List<PerspectiveBatchResult> results) {
        log.info("生成综合报告: sessionId={}", sessionId);

        InterviewSessionEntity session = interviewSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        Long dbSessionId = session.getId();

        // 解析会话级权重配置
        Map<Long, Double> sessionWeights = parsePerspectiveWeights(session.getPerspectiveWeights());

        // 计算加权综合分
        double weightedScore = 0;
        double totalWeight = 0;

        List<PerspectiveScoreDTO> perspectiveScoreDTOs = new ArrayList<>();
        Map<String, PerspectiveDetailDTO> perspectiveDetails = new LinkedHashMap<>();

        Set<String> allStrengths = new LinkedHashSet<>();
        Set<String> allImprovements = new LinkedHashSet<>();

        for (PerspectiveBatchResult result : results) {
            // 获取权重
            double weight = sessionWeights.getOrDefault(result.perspectiveId(), result.weight());

            weightedScore += result.score() * weight;
            totalWeight += weight;

            // 汇总优势和改进建议
            if (result.strengths() != null) allStrengths.addAll(result.strengths());
            if (result.improvements() != null) allImprovements.addAll(result.improvements());

            // 构建视角评分 DTO
            perspectiveScoreDTOs.add(new PerspectiveScoreDTO(
                    result.perspectiveId(),
                    dbSessionId,
                    result.perspectiveId(),
                    result.perspectiveName(),
                    result.icon(),
                    -1,
                    result.score(),
                    result.feedback(),
                    result.strengths() != null ? result.strengths() : List.of(),
                    result.improvements() != null ? result.improvements() : List.of(),
                    "COMPLETED",
                    null,
                    LocalDateTime.now().toString(),
                    LocalDateTime.now().toString()
            ));

            // 构建视角详情
            List<PerspectiveQuestionScoreDTO> questionScores = result.questionEvals().stream()
                    .map(q -> new PerspectiveQuestionScoreDTO(q.questionIndex(), q.score(), q.feedback(), q.question(),
                            q.userAnswer(), q.referenceAnswer(), q.keyPoints()))
                    .toList();

            perspectiveDetails.put(result.perspectiveCode(), new PerspectiveDetailDTO(
                    result.perspectiveId(),
                    result.perspectiveName(),
                    result.icon(),
                    result.score(),
                    result.feedback(),
                    result.strengths() != null ? result.strengths() : List.of(),
                    result.improvements() != null ? result.improvements() : List.of(),
                    questionScores
            ));
        }

        // 如果没有批量评估结果，从数据库读取
        if (results.isEmpty()) {
            return getComprehensiveReportFromDb(dbSessionId);
        }

        // 计算综合得分
        int comprehensiveScore = totalWeight > 0 ? (int) (weightedScore / totalWeight) : 0;

        // 调用 AI 生成综合评语（评价 + 发展建议）
        ComprehensiveFeedbackDTO feedback = generateComprehensiveFeedbackFromResults(session, results);

        // 更新会话记录
        session.setComprehensiveScore(comprehensiveScore);
        session.setComprehensiveFeedback(feedback.evaluation());
        session.setDevelopmentSuggestions(feedback.developmentSuggestions());
        session.setPerspectiveSummaryStatus(AsyncTaskStatus.COMPLETED);
        interviewSessionRepository.save(session);

        log.info("综合报告生成完成: sessionId={}, score={}", sessionId, comprehensiveScore);

        return new ComprehensiveReportDTO(
                session.getSessionId(),
                comprehensiveScore,
                perspectiveScoreDTOs,
                feedback.evaluation(),
                new ArrayList<>(allStrengths),
                new ArrayList<>(allImprovements),
                feedback.developmentSuggestions(),
                perspectiveDetails
        );
    }

    /**
     * 调用 AI 生成综合评语（基于各视角批量评估结果）
     * 返回评价和发展建议两部分
     */
    private ComprehensiveFeedbackDTO generateComprehensiveFeedbackFromResults(InterviewSessionEntity session,
                                                          List<PerspectiveBatchResult> results) {
        try {
            StringBuilder summaryBuilder = new StringBuilder();
            summaryBuilder.append("各视角评估汇总：\n\n");

            Map<Long, Double> sessionWeights = parsePerspectiveWeights(session.getPerspectiveWeights());

            for (PerspectiveBatchResult r : results) {
                double weight = sessionWeights.getOrDefault(r.perspectiveId(), r.weight());
                summaryBuilder.append(String.format("【%s】(权重%.0f%%) 得分：%d\n",
                        r.perspectiveName(), weight * 100, r.score()));
                summaryBuilder.append("评价：").append(r.feedback() != null ? r.feedback() : "无").append("\n\n");
            }

            String prompt = String.format("""
                    请根据以下各视角的评价，生成综合评价：

                    %s

                    请以JSON格式返回：
                    {"evaluation": "评价内容-对候选人面试表现的总体评价、优势和不足，150-200字", "developmentSuggestions": "发展建议-针对不足的具体改进建议，100-150字"}
                    """, summaryBuilder);

            ChatClient chatClient = chatClientBuilder.build();
            return structuredOutputInvoker.invoke(
                    chatClient,
                    "你是一个专业的面试官，负责对候选人进行综合评价。请客观、专业地进行评价。",
                    prompt,
                    new BeanOutputConverter<>(ComprehensiveFeedbackDTO.class),
                    ErrorCode.AI_SERVICE_ERROR,
                    "AI综合评语生成失败：",
                    "ComprehensiveFeedback",
                    log
            );

        } catch (Exception e) {
            log.warn("AI综合评语生成失败: {}", e.getMessage());
            return new ComprehensiveFeedbackDTO("综合评价生成失败，请稍后重试。", "综合评价生成失败，请稍后重试。");
        }
    }

    /**
     * 从数据库读取综合报告（不调用 LLM）
     */
    public ComprehensiveReportDTO getComprehensiveReportFromDb(Long sessionId) {
        InterviewSessionEntity session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        Integer overallScore = session.getComprehensiveScore() != null ? session.getComprehensiveScore() : 0;
        String evaluation = session.getComprehensiveFeedback();
        String developmentSuggestions = session.getDevelopmentSuggestions();

        List<PerspectiveScoreEntity> comprehensiveScores =
                perspectiveScoreRepository.findComprehensiveScoresBySessionId(sessionId);

        Map<Long, Double> sessionWeights = parsePerspectiveWeights(session.getPerspectiveWeights());

        List<PerspectiveScoreDTO> perspectiveScoreDTOs = new ArrayList<>();
        Map<String, PerspectiveDetailDTO> perspectiveDetails = new LinkedHashMap<>();
        List<String> allStrengths = new ArrayList<>();
        List<String> allImprovements = new ArrayList<>();

        for (PerspectiveScoreEntity cs : comprehensiveScores) {
            InterviewerRoleEntity role = cs.getPerspective();
            List<String> strengths = parseJsonList(cs.getStrengthsJson());
            List<String> improvements = parseJsonList(cs.getImprovementsJson());
            allStrengths.addAll(strengths);
            allImprovements.addAll(improvements);

            perspectiveScoreDTOs.add(new PerspectiveScoreDTO(
                    role.getId(), sessionId, role.getId(), role.getRoleName(), role.getIcon(),
                    -1, cs.getScore(), cs.getFeedback(), strengths, improvements,
                    cs.getStatus().name(), null,
                    cs.getCompletedAt() != null ? cs.getCompletedAt().toString() : null,
                    cs.getCreatedAt() != null ? cs.getCreatedAt().toString() : null
            ));

            // 从 InterviewAnswerEntity 获取完整的问题详情
            List<InterviewAnswerEntity> perspectiveAnswers =
                    interviewAnswerRepository.findBySessionIdAndCreatedByPerspectiveIdOrderByQuestionIndexAsc(sessionId, role.getId());
            List<PerspectiveQuestionScoreDTO> questionScores = new ArrayList<>();
            for (InterviewAnswerEntity answer : perspectiveAnswers) {
                if (answer.getQuestionIndex() < 0) continue;
                questionScores.add(new PerspectiveQuestionScoreDTO(
                        answer.getQuestionIndex(),
                        answer.getScore(),
                        answer.getFeedback(),
                        answer.getQuestion(),
                        answer.getUserAnswer(),
                        answer.getReferenceAnswer(),
                        parseJsonList(answer.getKeyPointsJson())
                ));
            }

            perspectiveDetails.put(role.getRoleCode(), new PerspectiveDetailDTO(
                    role.getId(), role.getRoleName(), role.getIcon(),
                    cs.getScore(), cs.getFeedback(), strengths, improvements, questionScores));
        }

        return new ComprehensiveReportDTO(
                session.getSessionId(), overallScore, perspectiveScoreDTOs,
                evaluation,
                new ArrayList<>(new LinkedHashSet<>(allStrengths)),
                new ArrayList<>(new LinkedHashSet<>(allImprovements)),
                developmentSuggestions,
                perspectiveDetails);
    }

    /**
     * 获取视角评分列表
     */
    public List<PerspectiveScoreDTO> getPerspectiveScores(Long sessionId) {
        List<PerspectiveScoreEntity> scores =
                perspectiveScoreRepository.findComprehensiveScoresBySessionId(sessionId);
        return scores.stream()
                .map(cs -> {
                    InterviewerRoleEntity role = cs.getPerspective();
                    return new PerspectiveScoreDTO(
                            role.getId(), sessionId, role.getId(), role.getRoleName(), role.getIcon(),
                            -1, cs.getScore(), cs.getFeedback(),
                            parseJsonList(cs.getStrengthsJson()),
                            parseJsonList(cs.getImprovementsJson()),
                            cs.getStatus().name(), null,
                            cs.getCompletedAt() != null ? cs.getCompletedAt().toString() : null,
                            cs.getCreatedAt() != null ? cs.getCreatedAt().toString() : null
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取指定视角详情
     */
    public PerspectiveDetailDTO getPerspectiveDetail(Long sessionId, Long perspectiveId) {
        InterviewerRoleEntity role = interviewerRoleRepository.findById(perspectiveId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "视角不存在"));

        List<InterviewAnswerEntity> perspectiveAnswers =
                interviewAnswerRepository.findBySessionIdAndCreatedByPerspectiveIdOrderByQuestionIndexAsc(sessionId, perspectiveId);

        if (perspectiveAnswers.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "未找到该视角的题目记录");
        }

        PerspectiveScoreEntity comprehensiveScore = perspectiveScoreRepository
                .findComprehensiveScoreBySessionIdAndPerspectiveId(sessionId, perspectiveId)
                .orElse(null);

        List<PerspectiveQuestionScoreDTO> questionScores = new ArrayList<>();
        int totalScore = 0;
        int scoredCount = 0;

        for (InterviewAnswerEntity answer : perspectiveAnswers) {
            if (answer.getQuestionIndex() < 0) continue;

            Integer score = answer.getScore();
            String feedback = answer.getFeedback();

            questionScores.add(new PerspectiveQuestionScoreDTO(
                    answer.getQuestionIndex(),
                    score,
                    feedback,
                    answer.getQuestion(),
                    answer.getUserAnswer(),
                    answer.getReferenceAnswer(),
                    parseJsonList(answer.getKeyPointsJson())
            ));

            if (score != null) {
                totalScore += score;
                scoredCount++;
            }
        }

        // 优先使用 AI 综合评估分，次选用平均分兜底
        int perspectiveScore;
        if (comprehensiveScore != null && comprehensiveScore.getScore() != null) {
            perspectiveScore = comprehensiveScore.getScore();
        } else {
            perspectiveScore = scoredCount > 0 ? (int) (totalScore / scoredCount) : 0;
        }

        return new PerspectiveDetailDTO(
                role.getId(),
                role.getRoleName(),
                role.getIcon(),
                perspectiveScore,
                comprehensiveScore != null ? comprehensiveScore.getFeedback() : null,
                comprehensiveScore != null ? parseJsonList(comprehensiveScore.getStrengthsJson()) : List.of(),
                comprehensiveScore != null ? parseJsonList(comprehensiveScore.getImprovementsJson()) : List.of(),
                questionScores
        );
    }

    // ========== 工具方法 ==========

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}

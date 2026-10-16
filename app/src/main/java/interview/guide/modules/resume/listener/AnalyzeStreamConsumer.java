package interview.guide.modules.resume.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.outbox.OutboxProperties;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.aimodel.service.AiVisionCapabilityResolver;
import interview.guide.modules.aimodel.service.AiVisionCapabilityResolver.VisionCandidate;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.resume.service.ResumeGradingService;
import interview.guide.modules.resume.service.ResumePersistenceService;
import interview.guide.modules.resume.service.ResumeVisionParseService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 简历分析 Stream 消费者
 * 负责从 Redis Stream 消费消息并执行 AI 分析（消息只带 resumeId，文本从 resumes 表读取）
 * <p>视觉识别接入：文本无效（扫描件提取为空/过少）或配置「视觉优先」且简历为 PDF 时，
 * 从存储下载原文件走视觉模型识别（主模型失败退化小模型），识别文本回写 resumeText 缓存后进入分析。
 */
@Slf4j
@Component
public class AnalyzeStreamConsumer extends AbstractStreamConsumer<AnalyzeStreamConsumer.AnalyzePayload> {

    private final ResumeGradingService gradingService;
    private final ResumePersistenceService persistenceService;
    private final ResumeRepository resumeRepository;
    private final ResumeVisionParseService visionParseService;
    private final AiVisionCapabilityResolver visionResolver;

    public AnalyzeStreamConsumer(
        RedisService redisService,
        OutboxProperties outboxProperties,
        ResumeGradingService gradingService,
        ResumePersistenceService persistenceService,
        ResumeRepository resumeRepository,
        ResumeVisionParseService visionParseService,
        AiVisionCapabilityResolver visionResolver
    ) {
        super(redisService, outboxProperties);
        this.gradingService = gradingService;
        this.persistenceService = persistenceService;
        this.resumeRepository = resumeRepository;
        this.visionParseService = visionParseService;
        this.visionResolver = visionResolver;
    }

    record AnalyzePayload(Long resumeId) {}

    @Override
    protected String taskDisplayName() {
        return "简历分析";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "analyze-consumer";
    }

    @Override
    protected AnalyzePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String resumeIdStr = data.get(AsyncTaskStreamConstants.FIELD_RESUME_ID);
        if (resumeIdStr == null) {
            log.warn("消息格式错误（缺少 resumeId），跳过: messageId={}", messageId);
            return null;
        }
        try {
            return new AnalyzePayload(Long.parseLong(resumeIdStr));
        } catch (NumberFormatException e) {
            log.warn("消息格式错误（resumeId 非数字），跳过: messageId={}, resumeId={}", messageId, resumeIdStr);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(AnalyzePayload payload) {
        return "resumeId=" + payload.resumeId();
    }

    @Override
    protected boolean acquireTask(AnalyzePayload payload, boolean reclaimed) {
        // 正常路径只允许从 PENDING/FAILED 抢占；PEL 回收（原消费者已死）放宽到僵尸 PROCESSING；COMPLETED 永不重做
        List<AsyncTaskStatus> allowedFrom = reclaimed
            ? List.of(AsyncTaskStatus.PENDING, AsyncTaskStatus.FAILED, AsyncTaskStatus.PROCESSING)
            : List.of(AsyncTaskStatus.PENDING, AsyncTaskStatus.FAILED);
        return resumeRepository.casAnalyzeStatus(payload.resumeId(), allowedFrom) > 0;
    }

    @Override
    protected void processBusiness(AnalyzePayload payload) {
        Long resumeId = payload.resumeId();
        ResumeEntity resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            log.warn("简历已被删除，跳过分析任务: resumeId={}", resumeId);
            return;
        }

        AnalyzeInput input = resolveResumeText(resume);
        ResumeAnalysisResponse analysis = gradingService.analyzeResume(input.resumeText(), input.layoutEvaluation());
        ResumeEntity latest = resumeRepository.findById(resumeId).orElse(null);
        if (latest == null) {
            log.warn("简历在分析期间被删除，跳过保存结果: resumeId={}", resumeId);
            return;
        }
        persistenceService.saveAnalysis(latest, analysis);
    }

    /** 分析输入：resumeText = 分析用简历文本；layoutEvaluation = 视觉排版评价（非视觉路径为空串） */
    private record AnalyzeInput(String resumeText, String layoutEvaluation) {}

    /**
     * 解析本次分析用的简历文本与排版评价：
     * <ul>
     *   <li>库内文本有效且未配置视觉优先 → 直接用文本，无排版评价（多数简历走此路径）</li>
     *   <li>文本无效（扫描件提取为空/过少）或配置「视觉优先」→ 且为 PDF → 视觉识别，
     *       纯简历文本回写 resumeText 缓存（reanalyze 免重复识别），排版评价仅当次分析使用</li>
     *   <li>视觉识别失败：文本仍有效则降级用文本继续；文本也无效则抛错走失败/重试</li>
     *   <li>非 PDF（DOCX/TXT 等不走视觉）：文本有效继续用，无效抛错</li>
     * </ul>
     */
    private AnalyzeInput resolveResumeText(ResumeEntity resume) {
        String content = resume.getResumeText() != null ? resume.getResumeText() : "";
        boolean textInsufficient = ResumeVisionParseService.isTextInsufficient(content);
        if (!textInsufficient) {
            List<VisionCandidate> candidates = visionResolver.resolveVisionCandidates();
            if (candidates.isEmpty() || !AiVisionCapabilityResolver.isVisionPriority(candidates)) {
                return new AnalyzeInput(content, "");
            }
        }
        if (!isPdfResume(resume)) {
            if (textInsufficient) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED,
                        "无法从文件中提取文本内容，请确保文件不是扫描版PDF");
            }
            return new AnalyzeInput(content, "");
        }
        try {
            ResumeVisionParseService.VisionParseResult vision = visionParseService.parseByVision(resume.getStorageKey());
            resume.setResumeText(vision.resumeText());
            resumeRepository.save(resume);
            log.info("视觉识别文本已回写缓存: resumeId={}, 长度={}, 排版评价长度={}",
                    resume.getId(), vision.resumeText().length(), vision.layoutEvaluation().length());
            return new AnalyzeInput(vision.resumeText(), vision.layoutEvaluation());
        } catch (Exception e) {
            log.warn("视觉识别失败: resumeId={}, error={}", resume.getId(), e.getMessage());
            if (textInsufficient) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED,
                        "无法识别简历内容（视觉识别失败：" + e.getMessage() + "）");
            }
            return new AnalyzeInput(content, "");
        }
    }

    /** 是否 PDF 简历：contentType 优先（探测结果），文件名后缀兜底 */
    private boolean isPdfResume(ResumeEntity resume) {
        String contentType = resume.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("pdf")) {
            return true;
        }
        String filename = resume.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    protected void markCompleted(AnalyzePayload payload) {
        updateAnalyzeStatus(payload.resumeId(), AsyncTaskStatus.COMPLETED, null);
    }

    @Override
    protected void markFailed(AnalyzePayload payload, String error) {
        updateAnalyzeStatus(payload.resumeId(), AsyncTaskStatus.FAILED, error);
    }

    @Override
    protected void retryMessage(AnalyzePayload payload, int retryCount) {
        Long resumeId = payload.resumeId();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_RESUME_ID, resumeId.toString(),
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("简历分析任务已重新入队: resumeId={}, retryCount={}", resumeId, retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: resumeId={}, error={}", resumeId, e.getMessage(), e);
            updateAnalyzeStatus(resumeId, AsyncTaskStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }

    /**
     * 更新分析状态（定向 UPDATE，避免读-改-写旧快照覆盖并发更新）
     */
    private void updateAnalyzeStatus(Long resumeId, AsyncTaskStatus status, String error) {
        try {
            resumeRepository.updateAnalyzeStatus(resumeId, status, error);
            log.debug("分析状态已更新: resumeId={}, status={}", resumeId, status);
        } catch (Exception e) {
            log.error("更新分析状态失败: resumeId={}, status={}, error={}", resumeId, status, e.getMessage(), e);
        }
    }

}

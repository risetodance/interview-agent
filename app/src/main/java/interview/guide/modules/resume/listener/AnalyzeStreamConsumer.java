package interview.guide.modules.resume.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.model.AsyncTaskStatus;
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
 * 负责从 Redis Stream 消费消息并执行 AI 分析
 * <p>视觉识别接入：消息文本无效（扫描件提取为空/过少）或配置「视觉优先」且简历为 PDF 时，
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
        ResumeGradingService gradingService,
        ResumePersistenceService persistenceService,
        ResumeRepository resumeRepository,
        ResumeVisionParseService visionParseService,
        AiVisionCapabilityResolver visionResolver
    ) {
        super(redisService);
        this.gradingService = gradingService;
        this.persistenceService = persistenceService;
        this.resumeRepository = resumeRepository;
        this.visionParseService = visionParseService;
        this.visionResolver = visionResolver;
    }

    record AnalyzePayload(Long resumeId, String content) {}

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
        String content = data.get(AsyncTaskStreamConstants.FIELD_CONTENT);
        if (resumeIdStr == null || content == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        return new AnalyzePayload(Long.parseLong(resumeIdStr), content);
    }

    @Override
    protected String payloadIdentifier(AnalyzePayload payload) {
        return "resumeId=" + payload.resumeId();
    }

    @Override
    protected void markProcessing(AnalyzePayload payload) {
        updateAnalyzeStatus(payload.resumeId(), AsyncTaskStatus.PROCESSING, null);
    }

    @Override
    protected void processBusiness(AnalyzePayload payload) {
        Long resumeId = payload.resumeId();
        if (!resumeRepository.existsById(resumeId)) {
            log.warn("简历已被删除，跳过分析任务: resumeId={}", resumeId);
            return;
        }

        AnalyzeInput input = resolveResumeText(payload);
        ResumeAnalysisResponse analysis = gradingService.analyzeResume(input.resumeText(), input.layoutEvaluation());
        ResumeEntity resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            log.warn("简历在分析期间被删除，跳过保存结果: resumeId={}", resumeId);
            return;
        }
        persistenceService.saveAnalysis(resume, analysis);
    }

    /** 分析输入：resumeText = 分析用简历文本；layoutEvaluation = 视觉排版评价（非视觉路径为空串） */
    private record AnalyzeInput(String resumeText, String layoutEvaluation) {}

    /**
     * 解析本次分析用的简历文本与排版评价：
     * <ul>
     *   <li>消息文本有效且未配置视觉优先 → 直接用文本，无排版评价（多数简历走此路径）</li>
     *   <li>文本无效（扫描件提取为空/过少）或配置「视觉优先」→ 且为 PDF → 视觉识别，
     *       纯简历文本回写 resumeText 缓存（reanalyze 免重复识别），排版评价仅当次分析使用</li>
     *   <li>视觉识别失败：文本仍有效则降级用文本继续；文本也无效则抛错走失败/重试</li>
     *   <li>非 PDF（DOCX/TXT 等不走视觉）：文本有效继续用，无效抛错</li>
     * </ul>
     */
    private AnalyzeInput resolveResumeText(AnalyzePayload payload) {
        String content = payload.content();
        boolean textInsufficient = ResumeVisionParseService.isTextInsufficient(content);
        if (!textInsufficient) {
            List<VisionCandidate> candidates = visionResolver.resolveVisionCandidates();
            if (candidates.isEmpty() || !AiVisionCapabilityResolver.isVisionPriority(candidates)) {
                return new AnalyzeInput(content, "");
            }
        }
        ResumeEntity resume = resumeRepository.findById(payload.resumeId()).orElse(null);
        if (resume == null || !isPdfResume(resume)) {
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
            log.warn("视觉识别失败: resumeId={}, error={}", payload.resumeId(), e.getMessage());
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
        String content = payload.content();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_RESUME_ID, resumeId.toString(),
                AsyncTaskStreamConstants.FIELD_CONTENT, content,
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
     * 更新分析状态
     */
    private void updateAnalyzeStatus(Long resumeId, AsyncTaskStatus status, String error) {
        try {
            resumeRepository.findById(resumeId).ifPresent(resume -> {
                resume.setAnalyzeStatus(status);
                resume.setAnalyzeError(error);
                resumeRepository.save(resume);
                log.debug("分析状态已更新: resumeId={}, status={}", resumeId, status);
            });
        } catch (Exception e) {
            log.error("更新分析状态失败: resumeId={}, status={}, error={}", resumeId, status, e.getMessage(), e);
        }
    }

}

package interview.guide.modules.resume.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.file.PdfPageImageRenderer;
import interview.guide.modules.aimodel.service.AiModelRegistry;
import interview.guide.modules.aimodel.service.AiVisionCapabilityResolver;
import interview.guide.modules.aimodel.service.AiVisionCapabilityResolver.VisionCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历视觉识别服务
 * <p>解决扫描版 PDF（文本层为空）与复杂排版 PDF（提取乱序）的解析问题：
 * PDF 逐页渲染为图片 → 视觉（多模态）模型识别为带阅读顺序的结构化文本（Markdown）+ 排版评价，
 * 识别文本替换原 resumeText 进入既有分析链路；排版评价仅作为当次分析的参考信息注入
 * （影响结构评分与建议），不落库、不改变分析响应结构。
 * <p>视觉候选链：CHAT 主模型 → SMALL_CHAT 小模型（均需 supports_vision=true），
 * 按序调用、失败退化下一候选，与 AiModelRegistry 热切换机制解耦（每次调用取当前槽位模型）。
 */
@Slf4j
@Service
public class ResumeVisionParseService {

    /**
     * 视觉识别结果：resumeText = 带阅读顺序的简历文本（Markdown，超页数截断时尾部带标注）；
     * layoutEvaluation = 排版评价（基于截图版式观察，供分析参考，可为空串）。
     */
    public record VisionParseResult(String resumeText, String layoutEvaluation) {}

    /** 文本有效阈值：提取文本 trim 后少于此字符数视为「解析失败/过少」，触发视觉兜底 */
    public static final int MIN_EFFECTIVE_TEXT_LENGTH = 100;

    private final AiVisionCapabilityResolver visionResolver;
    private final PdfPageImageRenderer pdfRenderer;
    private final FileStorageService storageService;
    private final AiModelRegistry aiModelRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<VisionParseResult> outputConverter;

    public ResumeVisionParseService(
            AiVisionCapabilityResolver visionResolver,
            PdfPageImageRenderer pdfRenderer,
            FileStorageService storageService,
            AiModelRegistry aiModelRegistry,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/resume-vision-parse-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/resume-vision-parse-user.st") Resource userPromptResource) throws IOException {
        this.visionResolver = visionResolver;
        this.pdfRenderer = pdfRenderer;
        this.storageService = storageService;
        this.aiModelRegistry = aiModelRegistry;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(VisionParseResult.class);
    }

    /**
     * 当前是否存在可用的视觉模型候选（上传接口据此决定：文本提取不到时放行异步识别，还是同步报错拦截）。
     */
    public boolean hasVisionCapability() {
        return !visionResolver.resolveVisionCandidates().isEmpty();
    }

    /**
     * 判断解析出的文本是否无效（null / 空白 / 少于 {@link #MIN_EFFECTIVE_TEXT_LENGTH} 字符）。
     */
    public static boolean isTextInsufficient(String text) {
        return text == null || text.trim().length() < MIN_EFFECTIVE_TEXT_LENGTH;
    }

    /**
     * 视觉识别简历 PDF：从存储下载原文件 → 逐页渲染 → 按候选顺序调用视觉模型。
     *
     * @param storageKey 简历文件存储键（RustFS）
     * @return 识别结果（简历文本 + 排版评价）；resumeText 超页数截断时尾部带标注
     * @throws BusinessException 无可用视觉模型 / 全部候选调用失败 / 识别结果过少
     */
    public VisionParseResult parseByVision(String storageKey) {
        List<VisionCandidate> candidates = visionResolver.resolveVisionCandidates();
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_CONFIGURED,
                    "未配置支持视觉的模型，无法进行简历识图");
        }
        byte[] pdfBytes = storageService.downloadFile(storageKey);
        PdfPageImageRenderer.RenderedPages pages = pdfRenderer.render(pdfBytes);

        String lastError = null;
        for (VisionCandidate candidate : candidates) {
            try {
                long start = System.currentTimeMillis();
                VisionParseResult result = invokeVisionModel(candidate, pages);
                if (isTextInsufficient(result.resumeText())) {
                    throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED,
                            "视觉识别返回内容过少（" + result.resumeText().trim().length() + " 字符）");
                }
                log.info("视觉识别简历成功: model={}, 页数={}, 耗时={}ms, 文本长度={}, 排版评价长度={}",
                        candidate.config().getModelName(), pages.pageImages().size(),
                        System.currentTimeMillis() - start, result.resumeText().length(),
                        result.layoutEvaluation() == null ? 0 : result.layoutEvaluation().length());
                return new VisionParseResult(
                        appendTruncationNote(result.resumeText(), pages),
                        result.layoutEvaluation() == null ? "" : result.layoutEvaluation().trim());
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("视觉模型识别失败，退化下一候选: role={}, model={}, error={}",
                        candidate.role(), candidate.config().getModelName(), e.getMessage());
            }
        }
        throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "视觉识别简历失败: " + lastError);
    }

    /**
     * 调用单个视觉候选识别全部页面图片（一次请求携带多页，保持跨页上下文连贯）。
     * <p>输出为 JSON（简历文本 + 排版评价），复用 {@link StructuredOutputInvoker#invokeWithMedia}
     * 的结构化解析 + 同模型重试 + 防注入边界策略；重试耗尽仍失败时抛异常，由候选退化链兜底。
     */
    private VisionParseResult invokeVisionModel(VisionCandidate candidate, PdfPageImageRenderer.RenderedPages pages) {
        var chatModel = aiModelRegistry.getActiveChatModel(candidate.role());
        if (chatModel == null) {
            throw new IllegalStateException("视觉候选槽位未加载模型: " + candidate.role());
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("pageCount", pages.pageImages().size());
        variables.put("truncatedNote", pages.truncated()
                ? "注意：原 PDF 共 " + pages.totalPages() + " 页，超出单次识别上限，仅提供前 "
                        + pages.pageImages().size() + " 页，后续内容缺失。"
                : "");
        String userPrompt = userPromptTemplate.render(variables);
        String systemPromptWithFormat = systemPromptTemplate.render() + "\n\n" + outputConverter.getFormat();
        List<Media> pageMedia = pages.pageImages().stream()
                .map(image -> new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(image)))
                .toList();
        return structuredOutputInvoker.invokeWithMedia(
                ChatClient.create(chatModel),
                systemPromptWithFormat,
                userPrompt,
                pageMedia,
                outputConverter,
                ErrorCode.RESUME_PARSE_FAILED,
                "视觉识别输出解析失败：",
                "视觉识别简历",
                log);
    }

    /**
     * 超页数截断时在识别文本尾部拼接标注（Java 侧拼接，不依赖模型自觉）。
     */
    private String appendTruncationNote(String text, PdfPageImageRenderer.RenderedPages pages) {
        if (!pages.truncated()) {
            return text;
        }
        return text + "\n\n> 注：该简历共 " + pages.totalPages() + " 页，超出单次识别上限 "
                + PdfPageImageRenderer.MAX_PAGES + " 页，以上内容仅覆盖前 "
                + pages.pageImages().size() + " 页。";
    }
}

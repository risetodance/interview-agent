package interview.guide.modules.resume.service;

import interview.guide.common.config.AppConfigProperties;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.file.FileValidationService;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.resume.listener.AnalyzeStreamProducer;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 简历上传服务
 * 处理简历上传、解析的业务逻辑
 * AI 分析改为异步处理，通过 Redis Stream 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeUploadService {

    private final ResumeParseService parseService;
    private final FileStorageService storageService;
    private final ResumePersistenceService persistenceService;
    private final AppConfigProperties appConfig;
    private final FileValidationService fileValidationService;
    private final AnalyzeStreamProducer analyzeStreamProducer;
    private final ResumeRepository resumeRepository;
    private final ResumeVisionParseService visionParseService;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 上传并分析简历（异步）
     *
     * @param file     简历文件
     * @param userId   用户ID
     * @param filename 前端显式传入的原始文件名（小程序 uni.uploadFile 发的是临时文件名，可空）
     * @return 上传结果（分析将异步进行）
     */
    public Map<String, Object> uploadAndAnalyze(org.springframework.web.multipart.MultipartFile file,
                                                Long userId, String filename) {
        // 1. 验证文件
        fileValidationService.validateFile(file, MAX_FILE_SIZE, "简历");

        String fileName = resolveOriginalFilename(filename, file);
        log.info("收到简历上传请求: {}, 大小: {} bytes", fileName, file.getSize());

        // 2. 验证文件类型
        String contentType = parseService.detectContentType(file);
        validateContentType(contentType);

        // 3. 检查简历是否已存在（去重，按用户）
        Optional<ResumeEntity> existingResume = persistenceService.findExistingResume(file, userId);
        if (existingResume.isPresent()) {
            return handleDuplicateResume(existingResume.get());
        }

        // 4. 解析简历文本：提取无效（扫描版/复杂排版）时，PDF 且配置了视觉模型才放行走后台识别，否则同步拦截
        String resumeText = parseService.parseResume(file);
        if (ResumeVisionParseService.isTextInsufficient(resumeText)) {
            if (!isPdfContentType(contentType) || !visionParseService.hasVisionCapability()) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法从文件中提取文本内容，请确保文件不是扫描版PDF");
            }
            log.info("简历文本提取无效，将走后台视觉识别: {}", fileName);
            resumeText = "";
        }

        // 5. 保存简历到RustFS
        String fileKey = storageService.uploadResume(file);
        String fileUrl = storageService.getFileUrl(fileKey);
        log.info("简历已存储到RustFS: {}", fileKey);

        // 6. 保存简历到数据库（状态为 PENDING）
        ResumeEntity savedResume = persistenceService.saveResume(file, fileName, resumeText, fileKey, fileUrl, userId);

        // 7. 发送分析任务到 Redis Stream（异步处理）
        analyzeStreamProducer.sendAnalyzeTask(savedResume.getId(), resumeText);

        log.info("简历上传完成，分析任务已入队: {}, resumeId={}", fileName, savedResume.getId());

        // 8. 返回结果（状态为 PENDING，前端可轮询获取最新状态）
        return Map.of(
            "resume", Map.of(
                "id", savedResume.getId(),
                "filename", savedResume.getOriginalFilename(),
                "analyzeStatus", AsyncTaskStatus.PENDING.name()
            ),
            "storage", Map.of(
                "fileKey", fileKey,
                "fileUrl", fileUrl,
                "resumeId", savedResume.getId()
            ),
            "duplicate", false
        );
    }

    /** 是否 PDF（按探测到的 MIME 类型判断，上传/重传路径使用） */
    private static boolean isPdfContentType(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("pdf");
    }

    /** 是否 PDF 简历（reanalyze 路径：contentType 优先，文件名后缀兜底） */
    private static boolean isPdfResume(ResumeEntity resume) {
        if (isPdfContentType(resume.getContentType())) {
            return true;
        }
        String filename = resume.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    /**
     * 解析原始文件名：优先前端显式传入（小程序 uni.uploadFile 无法指定 multipart 文件名，
     * 发出的是临时文件名如 tmp_xxx），其次 multipart 自带（Web 端），最后兜底 "未命名文件"
     */
    private String resolveOriginalFilename(String filename, org.springframework.web.multipart.MultipartFile file) {
        if (filename != null && !filename.trim().isEmpty()) {
            return filename.trim();
        }
        String original = file.getOriginalFilename();
        return (original != null && !original.isBlank()) ? original : "未命名文件";
    }

    /**
     * 验证文件类型
     */
    private void validateContentType(String contentType) {
        fileValidationService.validateContentTypeByList(
            contentType,
            appConfig.getAllowedTypes(),
            "不支持的文件类型: " + contentType
        );
    }

    /**
     * 处理重复简历
     */
    private Map<String, Object> handleDuplicateResume(ResumeEntity resume) {
        log.info("检测到重复简历，返回历史分析结果: resumeId={}", resume.getId());

        // 获取历史分析结果
        Optional<ResumeAnalysisResponse> analysisOpt = persistenceService.getLatestAnalysisAsDTO(resume.getId());

        // 已有分析结果，直接返回
        // 没有分析结果（可能之前分析失败），返回当前状态
        return analysisOpt.map(resumeAnalysisResponse -> Map.of(
                "analysis", resumeAnalysisResponse,
                "storage", Map.of(
                        "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                        "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                        "resumeId", resume.getId()
                ),
                "duplicate", true
        )).orElseGet(() -> Map.of(
                "resume", Map.of(
                        "id", resume.getId(),
                        "filename", resume.getOriginalFilename(),
                        "analyzeStatus", resume.getAnalyzeStatus() != null ? resume.getAnalyzeStatus().name() : AsyncTaskStatus.PENDING.name()
                ),
                "storage", Map.of(
                        "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                        "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                        "resumeId", resume.getId()
                ),
                "duplicate", true
        ));
    }

    /**
     * 重新分析简历（手动重试）
     * 从数据库获取简历文本并发送分析任务
     *
     * @param resumeId 简历ID
     * @param userId   用户ID（用于权限校验）
     */
    @Transactional
    public void reanalyze(Long resumeId, Long userId) {
        ResumeEntity resume = resumeRepository.findById(resumeId)
            .filter(r -> r.getUserId().equals(userId))
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在或无权限"));

        log.info("开始重新分析简历: resumeId={}, filename={}", resumeId, resume.getOriginalFilename());

        // 与初次上传逻辑对齐：每次重新分析都从原文件重新提取文本（不依赖历史缓存，
        // 换视觉模型 / 调整视觉优先配置后重试，识别效果能跟着更新），
        // 消费者再按「视觉优先 / 文本有效性」决策是否识图
        String resumeText;
        try {
            resumeText = parseService.downloadAndParseContent(resume.getStorageKey(), resume.getOriginalFilename());
        } catch (BusinessException e) {
            // 下载 / 解析硬失败：缓存文本仍有效则降级用缓存，否则按无文本走视觉或报错
            resumeText = resume.getResumeText();
            if (!ResumeVisionParseService.isTextInsufficient(resumeText)) {
                log.warn("重新提取文本失败，降级使用缓存文本: resumeId={}, error={}", resumeId, e.getMessage());
            }
        }
        if (ResumeVisionParseService.isTextInsufficient(resumeText)) {
            // 提取无效（扫描件/复杂排版）：PDF 且配置了视觉模型才入队走后台视觉识别，否则同步报错（与上传拦截一致）
            if (!isPdfResume(resume) || !visionParseService.hasVisionCapability()) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法获取简历文本内容");
            }
            log.info("重新提取仍无有效文本，将走后台视觉识别: resumeId={}", resumeId);
            resumeText = "";
        }
        resume.setResumeText(resumeText);

        // 更新状态为 PENDING
        resume.setAnalyzeStatus(AsyncTaskStatus.PENDING);
        resume.setAnalyzeError(null);
        resumeRepository.save(resume);

        // 发送分析任务到 Stream
        analyzeStreamProducer.sendAnalyzeTask(resumeId, resumeText);

        log.info("重新分析任务已发送: resumeId={}", resumeId);
    }
}

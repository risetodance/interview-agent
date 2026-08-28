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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        // 2.5 文件名兜底：前端未显式传 filename 且 multipart 自带的是小程序临时文件名时，
        // 生成时间戳默认名（临时名/未命名文件直接落库会让列表与详情显示 tmp_xxx）
        if (isTempFilename(fileName)) {
            fileName = defaultResumeFilename(contentType);
            log.info("未取到有效文件名，使用默认名: {}", fileName);
        }

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

        // 7. 发送分析任务到 Redis Stream（异步处理；本方法无外层事务，直接发送）
        sendAnalyzeTaskAfterCommit(savedResume.getId(), resumeText);

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

    /**
     * 事务提交后再发送分析任务到 Stream（无事务上下文则立即发送）。
     * <p>若在 @Transactional 事务内直接发送，消费者会与未提交数据并发：
     * 读到旧实体（旧 storageKey / 旧文本），其后的读-改-写还会用旧快照覆盖本次更新（lost update）。
     * 发送失败不影响已提交的 DB 结果，由 Producer 的 onSendFailed 兜底置 FAILED 状态。
     */
    private void sendAnalyzeTaskAfterCommit(Long resumeId, String content) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        analyzeStreamProducer.sendAnalyzeTask(resumeId, content);
                    } catch (Exception e) {
                        log.error("事务提交后发送分析任务失败: resumeId={}", resumeId, e);
                    }
                }
            });
        } else {
            analyzeStreamProducer.sendAnalyzeTask(resumeId, content);
        }
    }

    /**
     * 是否"无效"文件名：空 / 未命名兜底 / 小程序临时文件名特征。
     * <p>uni.uploadFile 的 multipart 文件名取自临时路径（如 tmp_ab12cd34.pdf、wxfile://、纯十六进制串），
     * 前端未显式传 filename 时这类名字会落库展示。检测刻意收窄（tmp 后须跟长随机串），
     * 避免误伤 "tmp整理的简历.pdf" 这类合法中文名。
     */
    static boolean isTempFilename(String filename) {
        if (filename == null || filename.isBlank() || "未命名文件".equals(filename.trim())) {
            return true;
        }
        String name = filename.trim();
        String lower = name.toLowerCase();
        return lower.matches("^tmp[_\\-]?[0-9a-z]{6,}(\\.\\w+)?$")     // tmp_ab12cd34.pdf
                || lower.startsWith("wxfile")                            // 小程序本地协议路径
                || lower.matches("^[a-f0-9\\-]{16,}(\\.\\w+)?$");         // 纯十六进制/UUID 随机串（含大写）
    }

    /**
     * 生成时间戳默认简历名（文件名兜底），扩展名按探测的 MIME 类型推断。
     */
    static String defaultResumeFilename(String contentType) {
        String extension = switch (contentType == null ? "" : contentType.toLowerCase()) {
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "text/plain" -> ".txt";
            default -> "";
        };
        return "简历_" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + extension;
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

        // 发送分析任务到 Stream（事务提交后发，避免消费者读到未提交数据）
        sendAnalyzeTaskAfterCommit(resumeId, resumeText);

        log.info("重新分析任务已发送: resumeId={}", resumeId);
    }

    /**
     * 重新上传简历文件（原记录替换：保留简历 ID 与关联面试记录）
     * 用新文件替换旧文件，更新时间戳并重新入队分析。
     *
     * @param resumeId 简历ID
     * @param file     新简历文件
     * @param userId   用户ID（用于权限校验）
     * @param filename 前端显式传入的原始文件名（小程序端临时文件名问题，可空）
     * @return 更新后的简历信息
     */
    @Transactional
    public Map<String, Object> reupload(Long resumeId, org.springframework.web.multipart.MultipartFile file,
                                        Long userId, String filename) {
        // 1. 验证文件
        fileValidationService.validateFile(file, MAX_FILE_SIZE, "简历");

        // 2. 验证文件类型
        String contentType = parseService.detectContentType(file);
        validateContentType(contentType);

        // 3. 获取简历并校验权限（记录旧存储键，替换成功后清理旧文件）
        ResumeEntity resume = resumeRepository.findById(resumeId)
            .filter(r -> r.getUserId().equals(userId))
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在或无权限"));
        String oldStorageKey = resume.getStorageKey();

        // 3.5 文件名兜底：旧版小程序重新上传不传 filename 参数，multipart 自带的是临时文件名——
        // 沿用原简历名（换文件不换名，符合"替换"语义；新版小程序已显式传 filename 不受影响）
        String fileName = resolveOriginalFilename(filename, file);
        if (isTempFilename(fileName)) {
            fileName = resume.getOriginalFilename();
            log.info("重新上传未取到有效文件名，沿用原简历名: {}", fileName);
        }
        log.info("收到简历重新上传请求: {}, 大小: {} bytes, resumeId={}", fileName, file.getSize(), resumeId);

        // 4. 解析新简历文本：提取无效（扫描版/复杂排版）时，PDF 且配置了视觉模型才放行走后台识别，否则同步拦截
        String resumeText = parseService.parseResume(file);
        if (ResumeVisionParseService.isTextInsufficient(resumeText)) {
            if (!isPdfContentType(contentType) || !visionParseService.hasVisionCapability()) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法从文件中提取文本内容，请确保文件不是扫描版PDF");
            }
            log.info("新简历文本提取无效，将走后台视觉识别: {}", fileName);
            resumeText = "";
        }

        // 5. 上传新文件到存储
        String fileKey = storageService.uploadResume(file);
        String fileUrl = storageService.getFileUrl(fileKey);
        log.info("新简历已存储到RustFS: {}", fileKey);

        // 6. 更新简历实体
        resume.setOriginalFilename(fileName);
        resume.setFileSize(file.getSize());
        resume.setContentType(contentType);
        resume.setStorageKey(fileKey);
        resume.setStorageUrl(fileUrl);
        resume.setResumeText(resumeText);
        resume.setUploadedAt(java.time.LocalDateTime.now());  // 更新时间戳（列表按上传时间倒序，重传后回到顶部）
        resume.setAnalyzeStatus(AsyncTaskStatus.PENDING);  // 重置分析状态
        resume.setAnalyzeError(null);
        resumeRepository.save(resume);

        // 7. 清理被替换的旧文件（孤儿文件会一直占用存储；清理失败仅告警，不影响主流程）
        if (oldStorageKey != null && !oldStorageKey.equals(fileKey)) {
            try {
                storageService.deleteResume(oldStorageKey);
                log.info("已清理被替换的旧简历文件: {}", oldStorageKey);
            } catch (Exception e) {
                log.warn("旧简历文件清理失败（不影响重新上传结果）: {}, error={}", oldStorageKey, e.getMessage());
            }
        }

        // 8. 发送分析任务到 Redis Stream（异步处理）——必须等事务提交后发：
        // 事务内发消息时，消费者会与未提交数据并发读写（读到旧 storageKey、旧快照回写覆盖本次更新）
        sendAnalyzeTaskAfterCommit(resumeId, resumeText);

        log.info("简历重新上传完成，分析任务已入队: {}, resumeId={}", fileName, resumeId);

        // 9. 返回结果
        return Map.of(
            "resume", Map.of(
                "id", resume.getId(),
                "filename", resume.getOriginalFilename(),
                "uploadedAt", resume.getUploadedAt().toString(),
                "analyzeStatus", AsyncTaskStatus.PENDING.name()
            ),
            "storage", Map.of(
                "fileKey", fileKey,
                "fileUrl", fileUrl
            )
        );
    }
}

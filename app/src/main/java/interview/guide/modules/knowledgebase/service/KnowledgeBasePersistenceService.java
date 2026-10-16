package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.outbox.OutboxMessageType;
import interview.guide.infrastructure.outbox.OutboxPublisher;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 知识库持久化服务
 * 处理所有需要事务的数据库操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBasePersistenceService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final OutboxPublisher outboxPublisher;

    /**
     * 处理重复知识库（更新访问计数）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleDuplicateKnowledgeBase(KnowledgeBaseEntity kb, String fileHash) {
        log.info("检测到重复知识库，返回已有记录: kbId={}", kb.getId());
        
        // 更新访问计数（在事务中）
        kb.incrementAccessCount();
        knowledgeBaseRepository.save(kb);
        
        // 重复知识库的向量数据应该已经存在，不需要重新向量化
        return Map.of(
            "knowledgeBase", Map.of(
                "id", kb.getId(),
                "name", kb.getName(),
                "fileSize", kb.getFileSize(),
                "contentLength", 0  // 不再存储content，所以长度为0
            ),
            "storage", Map.of(
                "fileKey", kb.getStorageKey() != null ? kb.getStorageKey() : "",
                "fileUrl", kb.getStorageUrl() != null ? kb.getStorageUrl() : ""
            ),
            "duplicate", true
        );
    }

    /**
     * 保存新知识库元数据到数据库
     *
     * @param originalFilename 原始文件名（小程序端由前端显式传入，multipart 自带的是临时文件名）
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseEntity saveKnowledgeBase(MultipartFile file, String name, String category,
                                                  String storageKey, String storageUrl, String fileHash, Long userId,
                                                  String originalFilename, String content) {
        try {
            KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
            kb.setFileHash(fileHash);
            kb.setName(name != null && !name.trim().isEmpty() ? name : extractNameFromFilename(originalFilename));
            kb.setCategory(category != null && !category.trim().isEmpty() ? category.trim() : null);
            kb.setOriginalFilename(originalFilename);
            kb.setFileSize(file.getSize());
            kb.setContentType(file.getContentType());
            kb.setStorageKey(storageKey);
            kb.setStorageUrl(storageUrl);
            kb.setUserId(userId);
            kb.setContent(content);

            KnowledgeBaseEntity saved = knowledgeBaseRepository.save(kb);
            log.info("知识库已保存: id={}, name={}, category={}, hash={}, userId={}", saved.getId(), saved.getName(), saved.getCategory(), fileHash, userId);

            // 与知识库落库同事务写 outbox（vectorStatus=PENDING 与入队原子提交，relay 兜底投递）
            outboxPublisher.publish(OutboxMessageType.KB_VECTORIZE,
                Map.of("kbId", saved.getId().toString()));

            return saved;
        } catch (Exception e) {
            log.error("保存知识库失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存知识库失败");
        }
    }

    /**
     * 重新入队向量化任务：解析文本落库 + 状态置 PENDING + outbox 写入同事务。
     * <p>解析文本原先只活在 Stream 消息里（丢了无法重建），现在落库为消费数据源。
     */
    @Transactional(rollbackFor = Exception.class)
    public void requeueVectorize(Long kbId, String content) {
        KnowledgeBaseEntity kb = knowledgeBaseRepository.findById(kbId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在"));

        kb.setContent(content);
        kb.setVectorStatus(VectorStatus.PENDING);
        kb.setVectorError(null);
        knowledgeBaseRepository.save(kb);

        outboxPublisher.publish(OutboxMessageType.KB_VECTORIZE,
            Map.of("kbId", kbId.toString()));

        log.info("知识库向量化任务已重新入 outbox: kbId={}", kbId);
    }

    /**
     * 从文件名提取知识库名称（去除扩展名）
     */
    private String extractNameFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "未命名知识库";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }
}


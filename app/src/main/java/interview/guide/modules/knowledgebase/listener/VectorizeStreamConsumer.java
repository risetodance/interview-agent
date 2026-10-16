package interview.guide.modules.knowledgebase.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.outbox.OutboxProperties;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库向量化 Stream 消费者
 * 负责从 Redis Stream 消费消息并执行向量化（消息只带 kbId，解析文本从 knowledge_bases.content 读取）
 */
@Slf4j
@Component
public class VectorizeStreamConsumer extends AbstractStreamConsumer<VectorizeStreamConsumer.VectorizePayload> {

    private final KnowledgeBaseVectorService vectorService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public VectorizeStreamConsumer(
        RedisService redisService,
        OutboxProperties outboxProperties,
        KnowledgeBaseVectorService vectorService,
        KnowledgeBaseRepository knowledgeBaseRepository
    ) {
        super(redisService, outboxProperties);
        this.vectorService = vectorService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    record VectorizePayload(Long kbId) {}

    @Override
    protected String taskDisplayName() {
        return "向量化";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.KB_VECTORIZE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.KB_VECTORIZE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.KB_VECTORIZE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "vectorize-consumer";
    }

    @Override
    protected VectorizePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String kbIdStr = data.get(AsyncTaskStreamConstants.FIELD_KB_ID);
        if (kbIdStr == null) {
            log.warn("消息格式错误（缺少 kbId），跳过: messageId={}", messageId);
            return null;
        }
        try {
            return new VectorizePayload(Long.parseLong(kbIdStr));
        } catch (NumberFormatException e) {
            log.warn("消息格式错误（kbId 非数字），跳过: messageId={}, kbId={}", messageId, kbIdStr);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(VectorizePayload payload) {
        return "kbId=" + payload.kbId();
    }

    @Override
    protected boolean acquireTask(VectorizePayload payload, boolean reclaimed) {
        // 正常路径只允许从 PENDING/FAILED 抢占；PEL 回收（原消费者已死）放宽到僵尸 PROCESSING；COMPLETED 永不重做
        List<VectorStatus> allowedFrom = reclaimed
            ? List.of(VectorStatus.PENDING, VectorStatus.FAILED, VectorStatus.PROCESSING)
            : List.of(VectorStatus.PENDING, VectorStatus.FAILED);
        return knowledgeBaseRepository.casVectorStatus(payload.kbId(), allowedFrom) > 0;
    }

    @Override
    protected void processBusiness(VectorizePayload payload) {
        KnowledgeBaseEntity kb = knowledgeBaseRepository.findById(payload.kbId()).orElse(null);
        if (kb == null) {
            log.warn("知识库已被删除，跳过向量化任务: kbId={}", payload.kbId());
            return;
        }
        if (kb.getContent() == null || kb.getContent().isBlank()) {
            log.warn("知识库解析文本为空，跳过向量化任务: kbId={}", payload.kbId());
            return;
        }
        vectorService.vectorizeAndStore(payload.kbId(), kb.getContent());
    }

    @Override
    protected void markCompleted(VectorizePayload payload) {
        updateVectorStatus(payload.kbId(), VectorStatus.COMPLETED, null);
    }

    @Override
    protected void markFailed(VectorizePayload payload, String error) {
        updateVectorStatus(payload.kbId(), VectorStatus.FAILED, error);
    }

    @Override
    protected void retryMessage(VectorizePayload payload, int retryCount) {
        Long kbId = payload.kbId();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_KB_ID, kbId.toString(),
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.KB_VECTORIZE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("向量化任务已重新入队: kbId={}, retryCount={}", kbId, retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: kbId={}, error={}", kbId, e.getMessage(), e);
            updateVectorStatus(kbId, VectorStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }

    /**
     * 更新向量化状态（定向 UPDATE，避免读-改-写旧快照覆盖并发更新）
     */
    private void updateVectorStatus(Long kbId, VectorStatus status, String error) {
        try {
            knowledgeBaseRepository.updateVectorStatus(kbId, status, error);
            log.debug("向量化状态已更新: kbId={}, status={}", kbId, status);
        } catch (Exception e) {
            log.error("更新向量化状态失败: kbId={}, status={}, error={}", kbId, status, e.getMessage(), e);
        }
    }

}

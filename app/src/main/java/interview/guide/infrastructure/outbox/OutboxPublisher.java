package interview.guide.infrastructure.outbox;

import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Outbox 发布器：Transactional Outbox 两段式的核心。
 * <ul>
 *   <li>生产侧：{@link #publish} 在业务事务内 INSERT outbox 记录（与业务状态置 PENDING 原子提交），
 *       并注册事务提交后立即投递的低延迟路径</li>
 *   <li>投递侧：{@link #dispatchPending} 将 PENDING 记录 XADD 到目标 Stream 并标记 SENT；
 *       单条失败按指数退避重试；由 afterCommit 立即触发 + 定时轮询兜底双路驱动</li>
 * </ul>
 * 消息体只带业务 ID（文本等大字段由消费端从 DB 读取），outbox 职责止于 SENT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final TypeReference<Map<String, String>> PAYLOAD_TYPE = new TypeReference<>() {};

    /** 连续投递失败达到该值即放弃本批（Redis 大概率不可用，等退避后的下一轮） */
    private static final int CONSECUTIVE_FAILURE_LIMIT = 3;

    private final OutboxMessageRepository outboxRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 在业务事务内写入一条 outbox 消息（须与业务数据更新同事务调用）。
     * <p>事务提交后立即尝试投递（低延迟主路径）；进程在提交后、投递前崩溃时由轮询兜底补发。
     *
     * @param type    消息类型（决定目标 Stream）
     * @param payload 载荷（只放业务 ID 等轻量字段，如 {"resumeId": 123}）
     */
    public void publish(OutboxMessageType type, Map<String, String> payload) {
        OutboxMessageEntity message = new OutboxMessageEntity();
        message.setMessageType(type);
        message.setPayload(objectMapper.writeValueAsString(payload));
        outboxRepository.save(message);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchPendingSafely();
                }
            });
        } else {
            // 无事务上下文（记录已由 save 的隐式事务提交落库），直接投递
            dispatchPendingSafely();
        }
    }

    /**
     * 投递一批 PENDING 记录到对应 Redis Stream。
     * <p>「锁定→投递→标记」在同一事务内：FOR UPDATE SKIP LOCKED 防多实例重复投递。
     * 单条失败退避后继续；连续失败达到上限放弃本批，避免 Redis 不可用时事务长时间持锁。
     *
     * @return 本批成功投递条数
     */
    public int dispatchPending() {
        Integer dispatched = transactionTemplate.execute(status -> {
            var batch = outboxRepository.lockPendingBatch(outboxProperties.getRelay().getBatchSize());
            if (batch.isEmpty()) {
                return 0;
            }
            int success = 0;
            int consecutiveFailures = 0;
            LocalDateTime now = LocalDateTime.now();
            for (OutboxMessageEntity message : batch) {
                try {
                    Map<String, String> fields = objectMapper.readValue(message.getPayload(), PAYLOAD_TYPE);
                    redisService.streamAdd(message.getMessageType().streamKey(), fields,
                        AsyncTaskStreamConstants.STREAM_MAX_LEN);
                    message.markSent(LocalDateTime.now());
                    success++;
                    consecutiveFailures = 0;
                } catch (Exception e) {
                    log.error("Outbox 投递失败: id={}, type={}, retryCount={}, error={}",
                        message.getId(), message.getMessageType(), message.getRetryCount(), e.getMessage());
                    message.markRetryFailed(outboxProperties.getRelay().getBackoffBaseMs(),
                        outboxProperties.getRelay().getMaxBackoffMs(), now);
                    if (++consecutiveFailures >= CONSECUTIVE_FAILURE_LIMIT) {
                        log.warn("Outbox 连续 {} 条投递失败，放弃本批等待退避后重试", consecutiveFailures);
                        break;
                    }
                }
            }
            return success;
        });
        return dispatched == null ? 0 : dispatched;
    }

    /** 清理已投递超过保留期的 SENT 记录，返回删除条数 */
    public int purgeExpiredSent() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusDays(outboxProperties.getRetentionDays());
        return transactionTemplate.execute(status -> outboxRepository.deleteSentBefore(expiredBefore));
    }

    /** 投递失败不影响业务结果（记录已落库，轮询兜底会补发），仅记录日志 */
    private void dispatchPendingSafely() {
        try {
            dispatchPending();
        } catch (Exception e) {
            log.error("Outbox 立即投递异常（将由轮询兜底）: {}", e.getMessage(), e);
        }
    }
}

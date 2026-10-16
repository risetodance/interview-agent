package interview.guide.common.async;

import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.outbox.OutboxProperties;
import interview.guide.infrastructure.redis.RedisService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Stream 消费者模板基类。
 * <p>
 * 将消费循环、CAS 抢占、ACK、重试、PEL 滞留回收与生命周期管理收敛到统一模板，
 * 子类仅关注业务处理逻辑。
 * <p>
 * 幂等语义：消息处理前先经 {@link #acquireTask} CAS 抢占（业务状态 PENDING/FAILED → PROCESSING），
 * 抢占失败说明任务已被并发消费者或重复消息处理（中）/完成，直接跳过；PEL 回收路径
 * （原消费者已死）放宽到允许抢占 PROCESSING 的僵尸状态，但任何路径都不重做已 COMPLETED 的任务。
 */
@Slf4j
public abstract class AbstractStreamConsumer<T> {

    private final RedisService redisService;
    private final OutboxProperties outboxProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private String consumerName;
    private volatile long lastClaimTimeMs;

    protected AbstractStreamConsumer(RedisService redisService, OutboxProperties outboxProperties) {
        this.redisService = redisService;
        this.outboxProperties = outboxProperties;
    }

    @PostConstruct
    public void init() {
        this.consumerName = consumerPrefix() + UUID.randomUUID().toString().substring(0, 8);

        try {
            redisService.createStreamGroup(streamKey(), groupName());
            log.info("Redis Stream 消费者组已创建或已存在: {}", groupName());
        } catch (Exception e) {
            log.error("创建消费者组时发生异常（可能已存在）: {}", e.getMessage(), e);
        }

        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName());
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        executorService.submit(this::consumeLoop);
        log.info("{}消费者已启动: consumerName={}", taskDisplayName(), consumerName);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdown();
        }
        log.info("{}消费者已关闭: consumerName={}", taskDisplayName(), consumerName);
    }

    private void consumeLoop() {
        while (running.get()) {
            try {
                reclaimStaleMessagesIfNeeded();

                redisService.streamConsumeMessages(
                    streamKey(),
                    groupName(),
                    consumerName,
                    AsyncTaskStreamConstants.BATCH_SIZE,
                    AsyncTaskStreamConstants.POLL_INTERVAL_MS,
                    (messageId, data) -> processMessage(messageId, data, false)
                );
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("消费者线程被中断");
                    break;
                }
                log.error("消费消息时发生错误: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 定期回收 PEL 滞留消息：消息被拉取后超过阈值仍未 ack，视为原消费者已死，
     * XAUTOCLAIM 认领到当前消费者重新处理（force 抢占，可打断僵尸 PROCESSING 状态）。
     */
    private void reclaimStaleMessagesIfNeeded() {
        long thresholdMs = outboxProperties.getConsumer().getClaimIdleThresholdMs();
        long now = System.currentTimeMillis();
        if (now - lastClaimTimeMs < thresholdMs) {
            return;
        }
        lastClaimTimeMs = now;
        try {
            Map<StreamMessageId, Map<String, String>> reclaimed = redisService.streamAutoClaim(
                streamKey(), groupName(), consumerName, thresholdMs, AsyncTaskStreamConstants.BATCH_SIZE);
            if (!reclaimed.isEmpty()) {
                log.warn("{}回收 PEL 滞留消息: stream={}, count={}", taskDisplayName(), streamKey(), reclaimed.size());
                reclaimed.forEach((messageId, data) -> processMessage(messageId, data, true));
            }
        } catch (Exception e) {
            log.error("{}回收 PEL 滞留消息失败: {}", taskDisplayName(), e.getMessage(), e);
        }
    }

    /**
     * 处理单条消息：CAS 抢占 → 业务处理 → 状态回写 → ack。
     * <p>protected 便于单测直接驱动（不起消费线程）。
     */
    protected void processMessage(StreamMessageId messageId, Map<String, String> data, boolean reclaimed) {
        T payload = parsePayload(messageId, data);
        if (payload == null) {
            ackMessage(messageId);
            return;
        }

        int retryCount = parseRetryCount(data);
        if (!acquireTask(payload, reclaimed)) {
            log.info("{}任务无需处理（处理中或已完成），跳过: {}, messageId={}, reclaimed={}",
                taskDisplayName(), payloadIdentifier(payload), messageId, reclaimed);
            ackMessage(messageId);
            return;
        }

        log.info("开始处理{}任务: {}, messageId={}, retryCount={}, reclaimed={}",
            taskDisplayName(), payloadIdentifier(payload), messageId, retryCount, reclaimed);

        try {
            processBusiness(payload);
            markCompleted(payload);
            ackMessage(messageId);
            log.info("{}任务完成: {}", taskDisplayName(), payloadIdentifier(payload));
        } catch (Exception e) {
            log.error("{}任务失败: {}, error={}", taskDisplayName(), payloadIdentifier(payload), e.getMessage(), e);
            // 先置 FAILED：重试消息的 CAS 依赖 PENDING/FAILED 可再入，避免状态卡在 PROCESSING
            markFailed(payload, truncateError(
                taskDisplayName() + "失败(重试" + retryCount + "/" + AsyncTaskStreamConstants.MAX_RETRY_COUNT + "): " + e.getMessage()
            ));
            if (retryCount < AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
                retryMessage(payload, retryCount + 1);
            }
            ackMessage(messageId);
        }
    }

    protected int parseRetryCount(Map<String, String> data) {
        try {
            return Integer.parseInt(data.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    private void ackMessage(StreamMessageId messageId) {
        try {
            redisService.streamAck(streamKey(), groupName(), messageId);
        } catch (Exception e) {
            log.error("确认消息失败: messageId={}, error={}", messageId, e.getMessage(), e);
        }
    }

    protected RedisService redisService() {
        return redisService;
    }

    protected abstract String taskDisplayName();

    protected abstract String streamKey();

    protected abstract String groupName();

    protected abstract String consumerPrefix();

    protected abstract String threadName();

    protected abstract T parsePayload(StreamMessageId messageId, Map<String, String> data);

    protected abstract String payloadIdentifier(T payload);

    /**
     * CAS 抢占任务：业务状态 PENDING/FAILED → PROCESSING，返回是否抢占成功。
     * <p>抢占失败（处理中/已完成）时模板直接跳过消息，保证消费幂等——relay 重复投递、
     * 组内并发重复均被此处拦截。
     *
     * @param reclaimed true 表示消息来自 PEL 回收（原消费者已死），此时允许抢占僵尸 PROCESSING 状态；
     *                  但 COMPLETED 任何情况下都不可重做
     */
    protected abstract boolean acquireTask(T payload, boolean reclaimed);

    protected abstract void processBusiness(T payload);

    protected abstract void markCompleted(T payload);

    protected abstract void markFailed(T payload, String error);

    protected abstract void retryMessage(T payload, int retryCount);
}

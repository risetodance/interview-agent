package interview.guide.modules.resume.listener;

import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.resume.service.ResumeGradingService;
import interview.guide.modules.resume.service.ResumePersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简历分析 Stream 消费者
 * 负责从 Redis Stream 消费消息并执行 AI 分析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeStreamConsumer {

    private final RedisService redisService;
    private final ResumeGradingService gradingService;
    private final ResumePersistenceService persistenceService;
    private final ResumeRepository resumeRepository;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private String consumerName;

    @PostConstruct
    public void init() {
        // 生成唯一的消费者名称（支持多实例部署）
        this.consumerName = AsyncTaskStreamConstants.RESUME_ANALYZE_CONSUMER_PREFIX + UUID.randomUUID().toString().substring(0, 8);

        // 创建消费者组（如果不存在）
        try {
            redisService.createStreamGroup(
                AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME
            );
            log.info("Redis Stream 消费者组已创建或已存在: {}", AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME);
        } catch (Exception e) {
            log.warn("创建消费者组时发生异常（可能已存在）: {}", e.getMessage());
        }

        // 启动消费者线程
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "analyze-consumer");
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        executorService.submit(this::consumeLoop);

        log.info("简历分析消费者已启动: consumerName={}", consumerName);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdown();
        }
        log.info("简历分析消费者已关闭: consumerName={}", consumerName);
    }

    /**
     * 消费循环
     */
    private void consumeLoop() {
        while (running.get()) {
            try {
                redisService.streamConsumeMessages(
                    AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                    AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME,
                    consumerName,
                    AsyncTaskStreamConstants.BATCH_SIZE,
                    AsyncTaskStreamConstants.POLL_INTERVAL_MS,
                    this::processMessage
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
     * 处理单条消息
     */
    private void processMessage(StreamMessageId messageId, Map<String, String> data) {
        String resumeIdStr = data.get(AsyncTaskStreamConstants.FIELD_RESUME_ID);
        String content = data.get(AsyncTaskStreamConstants.FIELD_CONTENT);
        String retryCountStr = data.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0");

        if (resumeIdStr == null || content == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            ackMessage(messageId);
            return;
        }

        Long resumeId = Long.parseLong(resumeIdStr);
        int retryCount = Integer.parseInt(retryCountStr);

        log.info("开始处理简历分析任务: resumeId={}, messageId={}, retryCount={}", resumeId, messageId, retryCount);

        try {
            // 1. 检查简历是否仍然存在（可能在分析过程中被删除）
            if (!resumeRepository.existsById(resumeId)) {
                log.warn("简历已被删除，跳过分析任务: resumeId={}", resumeId);
                ackMessage(messageId);
                return;
            }

            // 2. 更新状态为 PROCESSING
            updateAnalyzeStatus(resumeId, AsyncTaskStatus.PROCESSING, null);

            // 3. 执行 AI 分析
            ResumeAnalysisResponse analysis = gradingService.analyzeResume(content);

            // 4. 再次检查简历是否存在（分析期间可能被删除）
            ResumeEntity resume = resumeRepository.findById(resumeId).orElse(null);
            if (resume == null) {
                log.warn("简历在分析期间被删除，跳过保存结果: resumeId={}", resumeId);
                ackMessage(messageId);
                return;
            }
            persistenceService.saveAnalysis(resume, analysis);

            // 4. 更新状态为 COMPLETED
            updateAnalyzeStatus(resumeId, AsyncTaskStatus.COMPLETED, null);

            // 5. 确认消息
            ackMessage(messageId);

            log.info("简历分析任务完成: resumeId={}, score={}", resumeId, analysis.overallScore());

        } catch (Exception e) {
            log.error("简历分析任务失败: resumeId={}, error={}", resumeId, e.getMessage(), e);

            // 判断是否需要重试
            if (retryCount < AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
                // 重新入队（增加重试计数）
                retryMessage(resumeId, content, retryCount + 1);
            } else {
                // 超过最大重试次数，标记为失败
                String errorMsg = truncateError("分析失败(已重试" + retryCount + "次): " + e.getMessage());
                updateAnalyzeStatus(resumeId, AsyncTaskStatus.FAILED, errorMsg);
            }

            // 确认原消息（无论成功失败都要确认，否则会重复消费）
            ackMessage(messageId);
        }
    }

    /**
     * 重试消息（重新发送到 Stream）
     */
    private void retryMessage(Long resumeId, String content, int retryCount) {
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_RESUME_ID, resumeId.toString(),
                AsyncTaskStreamConstants.FIELD_CONTENT, content,
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService.streamAdd(
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
     * 确认消息
     */
    private void ackMessage(StreamMessageId messageId) {
        try {
            redisService.streamAck(
                AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME,
                messageId
            );
        } catch (Exception e) {
            log.error("确认消息失败: messageId={}, error={}", messageId, e.getMessage(), e);
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

    /**
     * 截断错误信息，避免超过数据库字段长度
     */
    private String truncateError(String error) {
        if (error == null) return null;
        return error.length() > 500 ? error.substring(0, 500) : error;
    }
}

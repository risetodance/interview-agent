package interview.guide.modules.interview.listener;

import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.interview.service.AnswerEvaluationService;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 面试评估 Stream 消费者
 * 负责从 Redis Stream 消费消息并执行评估
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluateStreamConsumer {

    private final RedisService redisService;
    private final InterviewSessionRepository sessionRepository;
    private final AnswerEvaluationService evaluationService;
    private final InterviewPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private String consumerName;

    @PostConstruct
    public void init() {
        // 生成唯一的消费者名称（支持多实例部署）
        this.consumerName = AsyncTaskStreamConstants.INTERVIEW_EVALUATE_CONSUMER_PREFIX + UUID.randomUUID().toString().substring(0, 8);

        // 创建消费者组（如果不存在）
        try {
            redisService.createStreamGroup(
                AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY,
                AsyncTaskStreamConstants.INTERVIEW_EVALUATE_GROUP_NAME
            );
            log.info("Redis Stream 消费者组已创建或已存在: {}", AsyncTaskStreamConstants.INTERVIEW_EVALUATE_GROUP_NAME);
        } catch (Exception e) {
            log.warn("创建消费者组时发生异常（可能已存在）: {}", e.getMessage());
        }

        // 启动消费者线程
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "evaluate-consumer");
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        executorService.submit(this::consumeLoop);

        log.info("评估消费者已启动: consumerName={}", consumerName);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdown();
        }
        log.info("评估消费者已关闭: consumerName={}", consumerName);
    }

    /**
     * 消费循环
     */
    private void consumeLoop() {
        while (running.get()) {
            try {
                redisService.streamConsumeMessages(
                    AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY,
                    AsyncTaskStreamConstants.INTERVIEW_EVALUATE_GROUP_NAME,
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
        String sessionId = data.get(AsyncTaskStreamConstants.FIELD_SESSION_ID);
        String retryCountStr = data.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0");

        if (sessionId == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            ackMessage(messageId);
            return;
        }

        int retryCount = Integer.parseInt(retryCountStr);

        log.info("开始处理评估任务: sessionId={}, messageId={}, retryCount={}", sessionId, messageId, retryCount);

        try {
            // 1. 更新状态为 PROCESSING
            updateEvaluateStatus(sessionId, AsyncTaskStatus.PROCESSING, null);

            // 2. 从数据库获取会话（使用 JOIN FETCH 加载简历）
            Optional<InterviewSessionEntity> sessionOpt = sessionRepository.findBySessionIdWithResume(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("会话已被删除，跳过评估任务: sessionId={}", sessionId);
                ackMessage(messageId);
                return;
            }

            InterviewSessionEntity session = sessionOpt.get();

            // 3. 解析问题列表
            List<InterviewQuestionDTO> questions = objectMapper.readValue(
                session.getQuestionsJson(),
                new TypeReference<>() {}
            );

            // 4. 恢复用户答案
            List<interview.guide.modules.interview.model.InterviewAnswerEntity> answers =
                persistenceService.findAnswersBySessionId(sessionId);
            for (interview.guide.modules.interview.model.InterviewAnswerEntity answer : answers) {
                int index = answer.getQuestionIndex();
                if (index >= 0 && index < questions.size()) {
                    InterviewQuestionDTO question = questions.get(index);
                    questions.set(index, question.withAnswer(answer.getUserAnswer()));
                }
            }

            // 5. 获取简历文本
            String resumeText = session.getResume().getResumeText();

            // 6. 执行评估
            InterviewReportDTO report = evaluationService.evaluateInterview(
                sessionId,
                resumeText,
                questions
            );

            // 7. 保存报告
            persistenceService.saveReport(sessionId, report);

            // 8. 更新状态为 COMPLETED
            updateEvaluateStatus(sessionId, AsyncTaskStatus.COMPLETED, null);

            // 9. 确认消息
            ackMessage(messageId);

            log.info("评估任务完成: sessionId={}, score={}", sessionId, report.overallScore());

        } catch (Exception e) {
            log.error("评估任务失败: sessionId={}, error={}", sessionId, e.getMessage(), e);

            // 判断是否需要重试
            if (retryCount < AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
                // 重新入队（增加重试计数）
                retryMessage(sessionId, retryCount + 1);
            } else {
                // 超过最大重试次数，标记为失败
                String errorMsg = truncateError("评估失败(已重试" + retryCount + "次): " + e.getMessage());
                updateEvaluateStatus(sessionId, AsyncTaskStatus.FAILED, errorMsg);
            }

            // 确认原消息（无论成功失败都要确认，否则会重复消费）
            ackMessage(messageId);
        }
    }

    /**
     * 重试消息（重新发送到 Stream）
     */
    private void retryMessage(String sessionId, int retryCount) {
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_SESSION_ID, sessionId,
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService.streamAdd(
                AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("评估任务已重新入队: sessionId={}, retryCount={}", sessionId, retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
            updateEvaluateStatus(sessionId, AsyncTaskStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }

    /**
     * 确认消息
     */
    private void ackMessage(StreamMessageId messageId) {
        try {
            redisService.streamAck(
                AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY,
                AsyncTaskStreamConstants.INTERVIEW_EVALUATE_GROUP_NAME,
                messageId
            );
        } catch (Exception e) {
            log.error("确认消息失败: messageId={}, error={}", messageId, e.getMessage(), e);
        }
    }

    /**
     * 更新评估状态
     */
    private void updateEvaluateStatus(String sessionId, AsyncTaskStatus status, String error) {
        try {
            sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
                session.setEvaluateStatus(status);
                session.setEvaluateError(error);
                sessionRepository.save(session);
                log.debug("评估状态已更新: sessionId={}, status={}", sessionId, status);
            });
        } catch (Exception e) {
            log.error("更新评估状态失败: sessionId={}, status={}, error={}", sessionId, status, e.getMessage(), e);
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

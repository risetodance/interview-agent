package interview.guide.common.async;

import interview.guide.infrastructure.outbox.OutboxProperties;
import interview.guide.infrastructure.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.stream.StreamMessageId;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Stream 消费者模板单元测试：CAS 抢占跳过、成功/失败路径的消息处理骨架
 * <p>直接驱动 processMessage（protected 入口）验证全流程，不起消费线程。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Stream 消费者模板测试")
class AbstractStreamConsumerTest {

    @Mock
    private RedisService redisService;

    private TestConsumer consumer;

    /** 最小化测试子类：记录各钩子调用 */
    static class TestConsumer extends AbstractStreamConsumer<Long> {
        int acquireCalls;
        int businessCalls;
        int completedCalls;
        int failedCalls;
        int retryCalls;
        boolean acquireResult = true;
        boolean businessThrows;

        TestConsumer(RedisService redisService, OutboxProperties properties) {
            super(redisService, properties);
        }

        @Override protected String taskDisplayName() { return "测试"; }
        @Override protected String streamKey() { return "test:stream"; }
        @Override protected String groupName() { return "test-group"; }
        @Override protected String consumerPrefix() { return "test-consumer-"; }
        @Override protected String threadName() { return "test-consumer"; }
        @Override protected Long parsePayload(StreamMessageId messageId, Map<String, String> data) {
            String id = data.get("id");
            return id == null ? null : Long.valueOf(id);
        }
        @Override protected String payloadIdentifier(Long payload) { return "id=" + payload; }
        @Override protected boolean acquireTask(Long payload, boolean reclaimed) {
            acquireCalls++;
            return acquireResult;
        }
        @Override protected void processBusiness(Long payload) {
            businessCalls++;
            if (businessThrows) {
                throw new IllegalStateException("boom");
            }
        }
        @Override protected void markCompleted(Long payload) { completedCalls++; }
        @Override protected void markFailed(Long payload, String error) { failedCalls++; }
        @Override protected void retryMessage(Long payload, int retryCount) { retryCalls++; }
    }

    @BeforeEach
    void setUp() {
        consumer = new TestConsumer(redisService, new OutboxProperties());
    }

    private void deliver(Map<String, String> data) {
        consumer.processMessage(new StreamMessageId(1, 1), data, false);
        verify(redisService).streamAck(eq("test:stream"), eq("test-group"), any(StreamMessageId.class));
    }

    @Test
    @DisplayName("CAS 抢占失败应跳过业务处理（幂等守卫）")
    void testSkipWhenAcquireFails() {
        consumer.acquireResult = false;

        deliver(Map.of("id", "5"));

        assertEquals(1, consumer.acquireCalls);
        assertEquals(0, consumer.businessCalls);
        assertEquals(0, consumer.completedCalls);
    }

    @Test
    @DisplayName("成功路径：业务处理 → markCompleted → ack")
    void testSuccessPath() {
        deliver(Map.of("id", "5"));

        assertEquals(1, consumer.businessCalls);
        assertEquals(1, consumer.completedCalls);
        assertEquals(0, consumer.failedCalls);
    }

    @Test
    @DisplayName("失败路径：markFailed → 重试未达上限重新入队 → ack")
    void testFailurePathWithRetry() {
        consumer.businessThrows = true;

        deliver(Map.of("id", "5", "retryCount", "1"));

        assertEquals(1, consumer.businessCalls);
        // 先置 FAILED（重试消息的 CAS 依赖 PENDING/FAILED 可再入）
        assertEquals(1, consumer.failedCalls);
        assertEquals(1, consumer.retryCalls);
    }

    @Test
    @DisplayName("失败路径：重试达上限不再入队")
    void testFailurePathExhausted() {
        consumer.businessThrows = true;

        deliver(Map.of("id", "5", "retryCount", "3"));

        assertEquals(1, consumer.failedCalls);
        assertEquals(0, consumer.retryCalls);
    }

    @Test
    @DisplayName("消息格式错误（payload 为 null）应直接 ack 跳过")
    void testMalformedMessageSkipped() {
        deliver(Map.of("noIdField", "x"));

        assertEquals(0, consumer.businessCalls);
    }
}

package interview.guide.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox 消息实体单元测试：状态流转与指数退避数学
 */
@DisplayName("Outbox 消息实体测试")
class OutboxMessageEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

    @Test
    @DisplayName("markSent 应置 SENT 并记录时间")
    void testMarkSent() {
        OutboxMessageEntity message = new OutboxMessageEntity();
        message.setStatus(OutboxMessageEntity.Status.PENDING);

        message.markSent(NOW);

        assertEquals(OutboxMessageEntity.Status.SENT, message.getStatus());
        assertEquals(NOW, message.getSentAt());
    }

    @Test
    @DisplayName("markRetryFailed 退避应按基数指数增长")
    void testRetryBackoffExponential() {
        OutboxMessageEntity message = new OutboxMessageEntity();
        long base = 1000;
        long max = 300000;

        // retryCount 1→3：退避 1s → 2s → 4s
        message.markRetryFailed(base, max, NOW);
        assertEquals(1, message.getRetryCount());
        assertEquals(NOW.plusSeconds(1), message.getNextRetryAt());

        message.markRetryFailed(base, max, NOW);
        assertEquals(2, message.getRetryCount());
        assertEquals(NOW.plusSeconds(2), message.getNextRetryAt());

        message.markRetryFailed(base, max, NOW);
        assertEquals(3, message.getRetryCount());
        assertEquals(NOW.plusSeconds(4), message.getNextRetryAt());
    }

    @Test
    @DisplayName("markRetryFailed 退避应封顶不超上限")
    void testRetryBackoffCapped() {
        OutboxMessageEntity message = new OutboxMessageEntity();
        long base = 1000;
        long max = 5000;

        // 反复失败使指数增长越过封顶：2^n 秒 > 5s 后应稳定在 5s
        for (int i = 0; i < 10; i++) {
            message.markRetryFailed(base, max, NOW);
        }
        assertEquals(10, message.getRetryCount());
        assertEquals(NOW.plusSeconds(5), message.getNextRetryAt());
    }

    @Test
    @DisplayName("新消息默认 PENDING 且立即可投")
    void testNewMessageDefaults() {
        OutboxMessageEntity message = new OutboxMessageEntity();

        assertEquals(OutboxMessageEntity.Status.PENDING, message.getStatus());
        assertEquals(0, message.getRetryCount());
        assertNull(message.getNextRetryAt());
        assertNotNull(message.getCreatedAt());
    }
}

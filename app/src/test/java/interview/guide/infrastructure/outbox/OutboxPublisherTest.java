package interview.guide.infrastructure.outbox;

import interview.guide.infrastructure.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Outbox 发布器单元测试：投递成功标记、失败退避、连续失败熔断、过期清理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox 发布器测试")
class OutboxPublisherTest {

    @Mock
    private OutboxMessageRepository outboxRepository;
    @Mock
    private RedisService redisService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxProperties properties;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties();
        properties.getRelay().setBatchSize(10);
        properties.getRelay().setBackoffBaseMs(1000);
        properties.getRelay().setMaxBackoffMs(300000);
        publisher = new OutboxPublisher(outboxRepository, redisService, objectMapper, properties, transactionTemplate);

        // TransactionTemplate 直通执行回调
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
    }

    private OutboxMessageEntity pendingMessage(Long id, OutboxMessageType type, Map<String, String> payload) {
        OutboxMessageEntity message = new OutboxMessageEntity();
        message.setId(id);
        message.setMessageType(type);
        message.setPayload(objectMapper.writeValueAsString(payload));
        return message;
    }

    @Test
    @DisplayName("dispatchPending：投递成功应标记 SENT 并返回成功数")
    void testDispatchSuccess() {
        OutboxMessageEntity resumeMsg = pendingMessage(1L, OutboxMessageType.RESUME_ANALYZE, Map.of("resumeId", "100"));
        OutboxMessageEntity kbMsg = pendingMessage(2L, OutboxMessageType.KB_VECTORIZE, Map.of("kbId", "200"));
        when(outboxRepository.lockPendingBatch(10)).thenReturn(List.of(resumeMsg, kbMsg));

        int dispatched = publisher.dispatchPending();

        assertEquals(2, dispatched);
        assertEquals(OutboxMessageEntity.Status.SENT, resumeMsg.getStatus());
        assertEquals(OutboxMessageEntity.Status.SENT, kbMsg.getStatus());
        assertNotNull(resumeMsg.getSentAt());
        // 各自投递到对应 Stream，载荷只含业务 ID
        verify(redisService).streamAdd(eq("resume:analyze:stream"), eq(Map.of("resumeId", "100")), anyInt());
        verify(redisService).streamAdd(eq("knowledgebase:vectorize:stream"), eq(Map.of("kbId", "200")), anyInt());
    }

    @Test
    @DisplayName("dispatchPending：单条失败应退避计数且不中断批次")
    void testDispatchFailureBackoff() {
        OutboxMessageEntity ok = pendingMessage(1L, OutboxMessageType.RESUME_ANALYZE, Map.of("resumeId", "100"));
        OutboxMessageEntity bad = pendingMessage(2L, OutboxMessageType.KB_VECTORIZE, Map.of("kbId", "200"));
        when(outboxRepository.lockPendingBatch(10)).thenReturn(List.of(ok, bad));
        // 显式 stub 两条流（STRICT_STUBS 下未匹配参数的调用会抛 PotentialStubbingProblem 而非返回默认值）
        when(redisService.streamAdd(eq("resume:analyze:stream"), anyMap(), anyInt())).thenReturn("1-1");
        when(redisService.streamAdd(eq("knowledgebase:vectorize:stream"), anyMap(), anyInt()))
            .thenThrow(new RuntimeException("redis down"));

        int dispatched = publisher.dispatchPending();

        // 好消息照常投递，坏消息退避
        assertEquals(1, dispatched);
        assertEquals(OutboxMessageEntity.Status.SENT, ok.getStatus());
        assertEquals(OutboxMessageEntity.Status.PENDING, bad.getStatus());
        assertEquals(1, bad.getRetryCount());
        assertNotNull(bad.getNextRetryAt());
    }

    @Test
    @DisplayName("dispatchPending：连续失败达上限应熔断本批（Redis 大概率不可用）")
    void testDispatchCircuitBreaker() {
        List<OutboxMessageEntity> batch = List.of(
            pendingMessage(1L, OutboxMessageType.RESUME_ANALYZE, Map.of("resumeId", "1")),
            pendingMessage(2L, OutboxMessageType.RESUME_ANALYZE, Map.of("resumeId", "2")),
            pendingMessage(3L, OutboxMessageType.RESUME_ANALYZE, Map.of("resumeId", "3")),
            pendingMessage(4L, OutboxMessageType.RESUME_ANALYZE, Map.of("resumeId", "4")));
        when(outboxRepository.lockPendingBatch(10)).thenReturn(batch);
        when(redisService.streamAdd(anyString(), anyMap(), anyInt()))
            .thenThrow(new RuntimeException("redis down"));

        int dispatched = publisher.dispatchPending();

        // 前 3 条失败触发熔断，第 4 条不再尝试
        assertEquals(0, dispatched);
        verify(redisService, times(3)).streamAdd(anyString(), anyMap(), anyInt());
        assertEquals(1, batch.get(2).getRetryCount());
        assertEquals(0, batch.get(3).getRetryCount());
    }

    @Test
    @DisplayName("dispatchPending：无待投递记录返回 0")
    void testDispatchEmpty() {
        when(outboxRepository.lockPendingBatch(10)).thenReturn(List.of());

        assertEquals(0, publisher.dispatchPending());
        verify(redisService, never()).streamAdd(anyString(), anyMap(), anyInt());
    }

    @Test
    @DisplayName("publish：应写库并立即尝试投递（无事务上下文路径）")
    void testPublishWithoutTransaction() {
        when(outboxRepository.lockPendingBatch(10)).thenReturn(List.of());

        publisher.publish(OutboxMessageType.INTERVIEW_EVALUATE, Map.of("sessionId", "s-1"));

        // 记录已落库，载荷为 JSON
        verify(outboxRepository).save(argThat(msg ->
            msg.getMessageType() == OutboxMessageType.INTERVIEW_EVALUATE
                && msg.getStatus() == OutboxMessageEntity.Status.PENDING
                && "{\"sessionId\":\"s-1\"}".equals(msg.getPayload())));
        // 立即投递被触发
        verify(outboxRepository).lockPendingBatch(10);
    }

    @Test
    @DisplayName("purgeExpiredSent：应按保留期删除")
    void testPurgeExpiredSent() {
        properties.setRetentionDays(7);
        when(outboxRepository.deleteSentBefore(any(LocalDateTime.class))).thenReturn(42);

        int purged = publisher.purgeExpiredSent();

        assertEquals(42, purged);
        verify(outboxRepository).deleteSentBefore(argThat(before ->
            before.isBefore(LocalDateTime.now().minusDays(6))
                && before.isAfter(LocalDateTime.now().minusDays(8))));
    }
}

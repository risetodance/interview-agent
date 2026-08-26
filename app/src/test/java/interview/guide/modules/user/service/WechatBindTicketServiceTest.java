package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.user.service.WechatBindTicketService.TicketPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WechatBindTicketService 单元测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>签发票据：openid/unionid 暂存 Redis</li>
 *   <li>读取票据：载荷解析（含 unionid 为空的格式）</li>
 *   <li>票据无效：过期/不存在抛业务异常</li>
 *   <li>失败计数：未达阈值不作废；达到阈值作废票据并抛错</li>
 * </ul>
 */
@DisplayName("微信绑定票据服务测试")
class WechatBindTicketServiceTest {

    @Mock
    private RedisService redisService;

    private WechatBindTicketService ticketService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ticketService = new WechatBindTicketService(redisService);
    }

    @Nested
    @DisplayName("签发与读取测试")
    class IssueAndPeekTests {

        @Test
        @DisplayName("签发票据：openid/unionid 以 5 分钟 TTL 写入 Redis")
        void testIssue_StoreIdentityWithTtl() {
            String ticket = ticketService.issue("openid-123", "unionid-456");

            assertFalse(ticket.isBlank());
            verify(redisService).set(eq("wechat:bind:ticket:" + ticket),
                    eq("openid-123|unionid-456"), eq(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("读取票据：unionid 存在时正确解析")
        void testPeek_WithUnionId_Parsed() {
            when(redisService.get("wechat:bind:ticket:t1")).thenReturn("openid-123|unionid-456");

            TicketPayload payload = ticketService.peek("t1");

            assertEquals("openid-123", payload.openid());
            assertEquals("unionid-456", payload.unionid());
        }

        @Test
        @DisplayName("读取票据：unionid 为空串时解析为 null")
        void testPeek_WithoutUnionId_ParsedAsNull() {
            when(redisService.get("wechat:bind:ticket:t1")).thenReturn("openid-123|");

            TicketPayload payload = ticketService.peek("t1");

            assertEquals("openid-123", payload.openid());
            assertNull(payload.unionid());
        }

        @Test
        @DisplayName("票据不存在或已过期时抛业务异常")
        void testPeek_TicketMissing_ThrowException() {
            when(redisService.get(anyString())).thenReturn(null);

            BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.peek("gone-ticket")
            );

            assertEquals(ErrorCode.WECHAT_TICKET_INVALID.getCode(), exception.getCode());
        }
    }

    @Nested
    @DisplayName("失败计数与作废测试")
    class FailureAndInvalidateTests {

        @Test
        @DisplayName("失败次数未达阈值：票据保留可继续尝试")
        void testRecordFailure_BelowThreshold_TicketKept() {
            when(redisService.increment(anyString())).thenReturn(3L);

            assertDoesNotThrow(() -> ticketService.recordFailure("t1"));

            verify(redisService, never()).delete(anyString());
        }

        @Test
        @DisplayName("失败次数达到上限：作废票据并抛业务异常")
        void testRecordFailure_ReachedThreshold_TicketInvalidated() {
            when(redisService.increment(anyString())).thenReturn(5L);

            BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.recordFailure("t1")
            );

            assertEquals(ErrorCode.WECHAT_BIND_TOO_MANY_FAILURES.getCode(), exception.getCode());
            verify(redisService).delete("wechat:bind:ticket:t1");
            verify(redisService).delete("wechat:bind:fail:t1");
        }

        @Test
        @DisplayName("作废票据：同时清理票据与失败计数")
        void testInvalidate_DeletesBothKeys() {
            ticketService.invalidate("t1");

            verify(redisService).delete("wechat:bind:ticket:t1");
            verify(redisService).delete("wechat:bind:fail:t1");
        }
    }
}

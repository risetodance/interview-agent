package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 微信绑定票据服务
 * <p>
 * 绑定流程分两步：第一步微信登录换 openid（此刻用户还没输凭证），
 * 第二步用户在关联页提交凭证。两步之间 openid 暂存 Redis，不下发前端。
 * <p>
 * 规则（模式同 EmailCodeService）：
 * - 票据 5 分钟有效（Redis TTL），绑定成功一次性消费
 * - 票据维度失败计数，错 5 次即作废票据，需重新微信登录（防凭证爆破）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatBindTicketService {

    private final RedisService redisService;

    /** 票据有效期 */
    static final Duration TICKET_TTL = Duration.ofMinutes(5);
    /** 最大凭证校验失败次数，超过后票据作废 */
    static final int MAX_FAILURES = 5;

    private static final String TICKET_PREFIX = "wechat:bind:ticket:";
    private static final String FAIL_PREFIX = "wechat:bind:fail:";

    /**
     * 票据载荷：openid 必有，unionid 可空（未绑定开放平台时微信不返回）
     */
    public record TicketPayload(String openid, String unionid) {}

    /**
     * 签发一次性票据：openid/unionid 暂存 Redis
     *
     * @return 票据（下发前端，关联接口凭票取回 openid）
     */
    public String issue(String openid, String unionid) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        // openid/unionid 均为微信生成的字母数字(-_)串，不含 "|"，拼接安全
        redisService.set(TICKET_PREFIX + ticket, openid + "|" + (unionid == null ? "" : unionid), TICKET_TTL);
        return ticket;
    }

    /**
     * 读取票据载荷（不消费，绑定成功时才 invalidate）
     *
     * @throws BusinessException 票据不存在或已过期/已作废
     */
    public TicketPayload peek(String ticket) {
        String raw = redisService.get(TICKET_PREFIX + ticket);
        if (raw == null) {
            throw new BusinessException(ErrorCode.WECHAT_TICKET_INVALID);
        }
        String[] parts = raw.split("\\|", -1);
        String unionid = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
        return new TicketPayload(parts[0], unionid);
    }

    /**
     * 作废票据（绑定成功时一次性消费，同时清理失败计数）
     */
    public void invalidate(String ticket) {
        redisService.delete(TICKET_PREFIX + ticket);
        redisService.delete(FAIL_PREFIX + ticket);
    }

    /**
     * 记一次凭证校验失败；达到上限作废票据并抛错（防爆破）
     *
     * @throws BusinessException 失败次数达到上限
     */
    public void recordFailure(String ticket) {
        String failKey = FAIL_PREFIX + ticket;
        long failures = redisService.increment(failKey);
        redisService.expire(failKey, TICKET_TTL);
        if (failures >= MAX_FAILURES) {
            redisService.delete(TICKET_PREFIX + ticket);
            redisService.delete(failKey);
            log.warn("微信绑定票据失败次数超限已作废: failures={}", failures);
            throw new BusinessException(ErrorCode.WECHAT_BIND_TOO_MANY_FAILURES);
        }
    }
}

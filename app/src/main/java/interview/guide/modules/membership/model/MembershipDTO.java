package interview.guide.modules.membership.model;

import interview.guide.modules.user.model.MembershipType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 会员信息 DTO
 */
@Getter
@Builder
public class MembershipDTO {

    /**
     * 会员类型
     */
    private final MembershipType membership;

    /**
     * 积分余额
     */
    private final Integer points;

    /**
     * 简历额度
     */
    private final Integer resumeQuota;

    /**
     * 面试额度
     */
    private final Integer interviewQuota;

    /**
     * AI调用额度
     */
    private final Integer aiCallQuota;

    /**
     * VIP到期时间
     */
    private final LocalDateTime vipExpiryDate;
}

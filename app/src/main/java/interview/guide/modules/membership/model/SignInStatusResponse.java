package interview.guide.modules.membership.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 签到状态响应
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignInStatusResponse {

    /**
     * 今天是否已签到
     */
    private boolean signedIn;

    /**
     * 连续签到天数
     */
    private int consecutiveDays;

    /**
     * 本次签到可获得的积分（未签到时返回）
     */
    private int pointsCanEarn;
}

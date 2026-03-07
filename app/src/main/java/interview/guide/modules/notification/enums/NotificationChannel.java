package interview.guide.modules.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知渠道枚举
 */
@Getter
@AllArgsConstructor
public enum NotificationChannel {
    /**
     * 站内通知
     */
    IN_APP("站内通知"),
    /**
     * 邮件通知
     */
    EMAIL("邮件"),
    /**
     * 短信通知
     */
    SMS("短信"),
    /**
     * 微信通知
     */
    WECHAT("微信");

    private final String description;
}

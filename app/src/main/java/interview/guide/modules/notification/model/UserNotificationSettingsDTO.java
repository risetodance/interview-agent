package interview.guide.modules.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户通知设置DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationSettingsDTO {
    private Long id;
    private Long userId;
    private Boolean inAppEnabled;
    private Boolean emailEnabled;
    private Boolean smsEnabled;
    private Boolean wechatEnabled;
}

package interview.guide.modules.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户通知设置实体
 */
@Entity
@Table(name = "user_notification_settings", indexes = {
    @Index(name = "idx_user_notification_settings_user_id", columnList = "user_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID
     */
    @Column(nullable = false, unique = true)
    private Long userId;

    /**
     * 站内通知是否开启
     */
    @Builder.Default
    private Boolean inAppEnabled = true;

    /**
     * 邮件通知是否开启
     */
    @Builder.Default
    private Boolean emailEnabled = false;

    /**
     * 短信通知是否开启
     */
    @Builder.Default
    private Boolean smsEnabled = false;

    /**
     * 微信通知是否开启
     */
    @Builder.Default
    private Boolean wechatEnabled = false;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

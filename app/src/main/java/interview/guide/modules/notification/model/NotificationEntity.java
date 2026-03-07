package interview.guide.modules.notification.model;

import interview.guide.modules.notification.enums.NotificationChannel;
import interview.guide.modules.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通知实体
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user_id", columnList = "user_id"),
    @Index(name = "idx_notification_user_is_read", columnList = "user_id, is_read"),
    @Index(name = "idx_notification_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 通知类型
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    /**
     * 通知渠道
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    /**
     * 通知标题
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * 通知内容
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * 是否已读
     */
    @Builder.Default
    private Boolean isRead = false;

    /**
     * 关联业务ID（如简历ID、知识库ID、面试ID）
     */
    private Long relatedId;

    /**
     * 关联业务类型（如 RESUME, KNOWLEDGEBASE, INTERVIEW）
     */
    @Column(length = 30)
    private String relatedType;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 阅读时间
     */
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

package interview.guide.modules.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户微信绑定实体（独立绑定表）
 * <p>
 * 设计要点：
 * <ul>
 *   <li>openid 按微信应用隔离，故唯一约束为 (channel, openid) 组合而非裸 openid</li>
 *   <li>(user_id, channel) 唯一：一个账号在同一渠道只能绑一个微信</li>
 *   <li>unionid 预留（小程序绑定微信开放平台后 code2session 才会返回），供将来多端微信登录识别同一用户</li>
 *   <li>解绑 = 删除记录，users 表不留脏列</li>
 * </ul>
 */
@Entity
@Table(name = "user_wechat_binding",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_wechat_binding_channel_openid", columnNames = {"channel", "openid"}),
        @UniqueConstraint(name = "uk_wechat_binding_user_channel", columnNames = {"user_id", "channel"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWechatBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 绑定的用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 微信应用渠道
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WechatChannel channel;

    /**
     * 微信openid（渠道内唯一）
     */
    @Column(nullable = false, length = 100)
    private String openid;

    /**
     * 微信unionid（可空，绑定开放平台后返回）
     */
    @Column(length = 100)
    private String unionid;

    /**
     * 绑定时间
     */
    @Column(name = "bound_at", nullable = false, updatable = false)
    private LocalDateTime boundAt;

    @PrePersist
    protected void onCreate() {
        boundAt = LocalDateTime.now();
    }
}

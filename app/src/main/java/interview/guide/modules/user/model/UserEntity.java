package interview.guide.modules.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username", unique = true),
    @Index(name = "idx_user_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名（唯一，不能为空）
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * 加密后的密码（BCrypt）
     */
    @Column(length = 255)
    private String password;

    /**
     * 邮箱（唯一）
     */
    @Column(unique = true, length = 100)
    private String email;

    /**
     * 昵称（可为空）
     */
    @Column(length = 50)
    private String nickname;

    /**
     * 头像URL（可为空）
     */
    @Column(length = 500)
    private String avatar;

    /**
     * 用户状态
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * 用户角色
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserRole role = UserRole.USER;

    /**
     * 积分（默认0）
     */
    private Integer points = 0;

    /**
     * 会员类型
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MembershipType membership = MembershipType.FREE;

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

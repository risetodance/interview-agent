package interview.guide.modules.user.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户资料 DTO（不包含密码）
 */
@Getter
@Builder
public class UserProfileDTO {

    /**
     * 用户ID
     */
    private final Long id;

    /**
     * 用户名
     */
    private final String username;

    /**
     * 邮箱
     */
    private final String email;

    /**
     * 昵称
     */
    private final String nickname;

    /**
     * 头像URL
     */
    private final String avatar;

    /**
     * 用户状态
     */
    private final UserStatus status;

    /**
     * 用户角色
     */
    private final UserRole role;

    /**
     * 积分
     */
    private final Integer points;

    /**
     * 会员类型
     */
    private final MembershipType membership;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;

    /**
     * 从 UserEntity 转换
     */
    public static UserProfileDTO fromEntity(UserEntity entity) {
        return UserProfileDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .nickname(entity.getNickname())
                .avatar(entity.getAvatar())
                .status(entity.getStatus())
                .role(entity.getRole())
                .points(entity.getPoints())
                .membership(entity.getMembership())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

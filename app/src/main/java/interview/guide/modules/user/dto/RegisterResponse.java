package interview.guide.modules.user.dto;

import interview.guide.modules.user.model.MembershipType;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.model.UserStatus;

import java.time.LocalDateTime;

/**
 * 注册响应
 */
public record RegisterResponse(
    Long id,
    String username,
    String email,
    String nickname,
    String avatar,
    UserStatus status,
    UserRole role,
    Integer points,
    MembershipType membership,
    LocalDateTime createdAt
) {}

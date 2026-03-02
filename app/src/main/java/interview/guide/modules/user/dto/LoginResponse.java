package interview.guide.modules.user.dto;

/**
 * 登录响应
 */
public record LoginResponse(
    String token,
    Long userId,
    String username,
    String role
) {}

package interview.guide.common.exception;

import lombok.Getter;

/**
 * JWT 认证异常
 * 用于处理 JWT 认证过程中的各种异常情况
 */
@Getter
public class JwtAuthenticationException extends BusinessException {

    /**
     * 认证失败：Token 无效或已过期
     */
    public static final JwtAuthenticationException TOKEN_INVALID =
            new JwtAuthenticationException(ErrorCode.UNAUTHORIZED, "Token无效或已过期");

    /**
     * 认证失败：Token 为空
     */
    public static final JwtAuthenticationException TOKEN_EMPTY =
            new JwtAuthenticationException(ErrorCode.UNAUTHORIZED, "Token不能为空");

    /**
     * 认证失败：无法获取用户信息
     */
    public static final JwtAuthenticationException USER_NOT_FOUND =
            new JwtAuthenticationException(ErrorCode.UNAUTHORIZED, "无法获取当前用户信息");

    public JwtAuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public JwtAuthenticationException(ErrorCode errorCode) {
        super(errorCode);
    }
}

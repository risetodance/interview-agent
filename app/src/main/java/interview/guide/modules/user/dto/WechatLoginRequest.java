package interview.guide.modules.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信小程序登录请求
 */
public record WechatLoginRequest(
    /**
     * 微信授权码，用于换取openid（5分钟有效、一次性）
     */
    @NotBlank(message = "授权码不能为空")
    String code
) {}

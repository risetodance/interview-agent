package interview.guide.modules.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信网页扫码登录请求
 */
public record WechatScanLoginRequest(
    /**
     * 微信授权码，用于换取openid
     */
    @NotBlank(message = "授权码不能为空")
    String code
) {}

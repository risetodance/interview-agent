package interview.guide.modules.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信小程序登录请求
 */
public record WechatLoginRequest(
    /**
     * 微信授权码，用于换取openid
     */
    @NotBlank(message = "授权码不能为空")
    String code,

    /**
     * 加密数据（可选，用于获取手机号）
     */
    String encryptedData,

    /**
     * 加密算法的初始向量（可选，配合encryptedData使用）
     */
    String iv
) {}

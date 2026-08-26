package interview.guide.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 微信登录/绑定相关请求/响应 DTO 集合
 * 一个文件承载小而相关的 record，避免 dto 包碎片化（模式同 EmailAuthDTOs）
 */
public final class WechatAuthDTOs {

    private WechatAuthDTOs() {
    }

    /**
     * 微信小程序登录响应
     * needsBind=true 表示该微信未绑定任何账号，login 为 null；
     * 前端凭 ticket 进入关联账号页，openid 暂存后端 Redis，不下发
     */
    public record WechatLoginResponse(
        boolean needsBind,
        String ticket,
        LoginResponse login
    ) {}

    /**
     * 关联通道一：账号/邮箱 + 密码
     */
    public record WechatBindByPasswordRequest(
        @NotBlank(message = "票据不能为空")
        String ticket,

        @NotBlank(message = "账号不能为空")
        String account,

        @NotBlank(message = "密码不能为空")
        String password
    ) {}

    /**
     * 关联通道二：邮箱 + 验证码
     */
    public record WechatBindByEmailRequest(
        @NotBlank(message = "票据不能为空")
        String ticket,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "验证码不能为空")
        String code
    ) {}
}

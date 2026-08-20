package interview.guide.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邮箱验证码相关请求/响应 DTO 集合
 * 一个文件承载小而相关的 record，避免 dto 包碎片化
 */
public final class EmailAuthDTOs {

    private EmailAuthDTOs() {
    }

    /**
     * 发送验证码请求
     * scene 取值：LOGIN / RESET_PASSWORD / CHANGE_PASSWORD / BIND_EMAIL
     */
    public record SendCodeRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "场景不能为空")
        String scene
    ) {}

    /**
     * 邮箱验证码登录请求
     */
    public record EmailLoginRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "验证码不能为空")
        String code
    ) {}

    /**
     * 邮箱验证码登录响应
     * needsRegister=true 表示邮箱未注册，前端进入两步式第二步（设置用户名与密码）；
     * 此时 login 为 null，验证码已还原，第二步提交时无需重新收码
     */
    public record EmailLoginResponse(
        boolean needsRegister,
        LoginResponse login
    ) {}

    /**
     * 邮箱验证码注册请求（两步式第二步）
     */
    public record EmailRegisterRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "验证码不能为空")
        String code,

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度应在3-50个字符之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度应在6-100个字符之间")
        String password,

        @Size(max = 50, message = "昵称长度不能超过50个字符")
        String nickname
    ) {}

    /**
     * 忘记密码-重置密码请求（未登录，凭邮箱验证码）
     */
    public record PasswordResetRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "验证码不能为空")
        String code,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度应在6-100个字符之间")
        String newPassword
    ) {}

    /**
     * 登录态-邮箱验证码修改密码请求
     * 验证码发送至当前账号绑定邮箱（scene=CHANGE_PASSWORD）
     */
    public record ChangePasswordByEmailRequest(
        @NotBlank(message = "验证码不能为空")
        String code,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度应在6-100个字符之间")
        String newPassword
    ) {}

    /**
     * 绑定/换绑邮箱请求
     * 验证码发送至新邮箱（scene=BIND_EMAIL），新邮箱单验证即完成换绑
     */
    public record BindEmailRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "验证码不能为空")
        String code
    ) {}
}

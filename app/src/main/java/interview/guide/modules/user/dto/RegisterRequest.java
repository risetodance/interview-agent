package interview.guide.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求
 */
public record RegisterRequest(
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度应在3-50个字符之间")
    String username,

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度应在6-100个字符之间")
    String password,

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    String email,

    // 邮箱验证码（scene=REGISTER）。不加 @NotBlank：两步式邮箱注册路径构造时传 null
    // （该路径已用 LOGIN 码验证过邮箱所有权），web 直注入口由 Controller 显式校验非空，
    // 避免两套校验标准打架
    @Size(max = 10, message = "验证码长度不正确")
    String code,

    @Size(max = 50, message = "昵称长度不能超过50个字符")
    String nickname
) {}

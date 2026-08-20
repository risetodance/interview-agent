package interview.guide.modules.user.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.annotation.RateLimit;
import interview.guide.common.annotation.RateLimit.Dimension;
import interview.guide.common.result.Result;
import interview.guide.modules.user.dto.EmailAuthDTOs.BindEmailRequest;
import interview.guide.modules.user.dto.EmailAuthDTOs.ChangePasswordByEmailRequest;
import interview.guide.modules.user.dto.EmailAuthDTOs.EmailLoginRequest;
import interview.guide.modules.user.dto.EmailAuthDTOs.EmailLoginResponse;
import interview.guide.modules.user.dto.EmailAuthDTOs.EmailRegisterRequest;
import interview.guide.modules.user.dto.EmailAuthDTOs.PasswordResetRequest;
import interview.guide.modules.user.dto.EmailAuthDTOs.SendCodeRequest;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.service.EmailAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import interview.guide.common.annotation.RateLimit.TimeUnit;

/**
 * 邮箱验证码认证控制器
 * <p>
 * 匿名接口（/api/auth/** 已在 SecurityConfig permitAll 与 JwtAuthenticationFilter 白名单）：
 * - POST /api/auth/email/code/send      发送验证码
 * - POST /api/auth/email/login          邮箱验证码登录（两步式第一步，未注册返回 needsRegister）
 * - POST /api/auth/email/register       邮箱验证码注册（两步式第二步）
 * - POST /api/auth/password/reset       忘记密码重置
 * <p>
 * 登录态接口：
 * - POST /api/users/password/change-by-email  邮箱验证码修改密码
 * - PUT  /api/users/email/bind                绑定/换绑邮箱
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class EmailAuthController {

    private final EmailAuthService emailAuthService;

    /**
     * 发送邮箱验证码
     * POST /api/auth/email/code/send
     * IP 维度限流防脚本批量发信（业务层另有 60s 单邮箱限发）。
     * F6：去掉 GLOBAL 维度——原全局 10/min 由全体用户共享，并发登录时会互相误伤
     */
    @PostMapping("/api/auth/email/code/send")
    @RateLimit(dimensions = {Dimension.IP},
            count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("收到发送邮箱验证码请求: email={}, scene={}", request.email(), request.scene());
        emailAuthService.sendCode(request.email(), request.scene());
        return Result.success("验证码已发送，请查收邮件", null);
    }

    /**
     * 邮箱验证码登录（两步式第一步）
     * POST /api/auth/email/login
     * S8：匿名验证入口，补 IP 限流防验证码爆破
     */
    @PostMapping("/api/auth/email/login")
    @RateLimit(dimensions = {Dimension.IP},
            count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<EmailLoginResponse> loginByEmail(@Valid @RequestBody EmailLoginRequest request) {
        log.info("收到邮箱验证码登录请求: email={}", request.email());
        EmailLoginResponse response = emailAuthService.loginByEmail(request.email(), request.code());
        return Result.success(response.needsRegister() ? "该邮箱未注册，请设置账号信息" : "登录成功", response);
    }

    /**
     * 邮箱验证码注册（两步式第二步：设置用户名与密码）
     * POST /api/auth/email/register
     * S8：匿名验证入口，补 IP 限流防验证码爆破
     */
    @PostMapping("/api/auth/email/register")
    @RateLimit(dimensions = {Dimension.IP},
            count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<LoginResponse> registerByEmail(@Valid @RequestBody EmailRegisterRequest request) {
        log.info("收到邮箱验证码注册请求: email={}, username={}", request.email(), request.username());
        LoginResponse response = emailAuthService.registerByEmail(request);
        return Result.success("注册成功", response);
    }

    /**
     * 忘记密码：凭邮箱验证码重置密码
     * POST /api/auth/password/reset
     */
    @PostMapping("/api/auth/password/reset")
    @RateLimit(dimensions = {Dimension.IP},
            count = 5, interval = 10, timeUnit = TimeUnit.MINUTES)
    public Result<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        log.info("收到重置密码请求: email={}", request.email());
        emailAuthService.resetPassword(request.email(), request.code(), request.newPassword());
        return Result.success("密码重置成功，请使用新密码登录", null);
    }

    /**
     * 登录态：邮箱验证码方式修改密码（验证码发往当前账号绑定邮箱）
     * POST /api/users/password/change-by-email
     */
    @PostMapping("/api/users/password/change-by-email")
    public Result<Void> changePasswordByEmail(@CurrentUser Long userId,
                                              @Valid @RequestBody ChangePasswordByEmailRequest request) {
        log.info("收到邮箱验证码修改密码请求: userId={}", userId);
        emailAuthService.changePasswordByEmail(userId, request.code(), request.newPassword());
        return Result.success("密码修改成功", null);
    }

    /**
     * 登录态：绑定/换绑邮箱（新邮箱验证码单验证）
     * PUT /api/users/email/bind
     */
    @PutMapping("/api/users/email/bind")
    public Result<Void> bindEmail(@CurrentUser Long userId,
                                  @Valid @RequestBody BindEmailRequest request) {
        log.info("收到绑定邮箱请求: userId={}, email={}", userId, request.email());
        emailAuthService.bindEmail(userId, request.email(), request.code());
        return Result.success("邮箱绑定成功", null);
    }
}

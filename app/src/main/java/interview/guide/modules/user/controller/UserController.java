package interview.guide.modules.user.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.annotation.RateLimit;
import interview.guide.common.annotation.RateLimit.Dimension;
import interview.guide.common.annotation.RateLimit.TimeUnit;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.modules.user.dto.LoginRequest;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.PasswordChangeRequest;
import interview.guide.modules.user.dto.ProfileUpdateRequest;
import interview.guide.modules.user.dto.RegisterRequest;
import interview.guide.modules.user.dto.RegisterResponse;
import interview.guide.modules.user.model.UserProfileDTO;
import interview.guide.modules.user.service.EmailCodeService;
import interview.guide.modules.user.service.EmailNormalizer;
import interview.guide.modules.user.service.UserLoginService;
import interview.guide.modules.user.service.UserQueryService;
import interview.guide.modules.user.service.UserRegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 * 处理用户认证和个人信息相关请求
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserRegisterService registerService;
    private final UserLoginService loginService;
    private final UserQueryService queryService;
    private final EmailCodeService emailCodeService;

    /**
     * 用户注册（web 直注入口）
     * POST /api/auth/register
     * 需要邮箱验证码（scene=REGISTER）：先经 POST /api/auth/email/code（scene=REGISTER）获取验证码，
     * 随邮箱/密码一并提交；两步式邮箱注册（registerByEmail）不经过本方法，已由 LOGIN 码完成邮箱所有权验证
     */
    @PostMapping("/api/auth/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("收到用户注册请求: username={}", request.username());
        // 与发码入口使用同一份规范化邮箱拼 Redis key 及查库，
        // 否则大小写/首尾空格变体会导致验码 key 漂移，误报“验证码已过期”
        String email = EmailNormalizer.normalize(request.email());

        // 冲突预检先于验码（F1 原则）：用户名/邮箱已存在时直接报错，不消耗验证码
        if (queryService.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (queryService.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 校验注册验证码（通过即消费，一次性使用）
        if (request.code() == null || request.code().isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID, "请输入邮箱验证码");
        }
        emailCodeService.verifyCode(email, EmailCodeService.Scene.REGISTER, request.code());

        RegisterResponse response = registerService.register(request);
        return Result.success("注册成功", response);
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/api/auth/login")
    @RateLimit(dimensions = {Dimension.IP},
            count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("收到用户登录请求: username={}", request.username());
        LoginResponse response = loginService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * 退出登录
     * POST /api/auth/logout
     * JWT 为无状态签发，登出以客户端删除 token 为准；本端点为前端登出调用提供统一落点
     * （此前该路径不存在，前端调用落进静态资源处理器报 NoResourceFoundException）。
     * 幂等设计：未携带 token 调用也返回成功；预留后续 token 黑名单/登出审计扩展
     */
    @PostMapping("/api/auth/logout")
    public Result<Void> logout() {
        return Result.success("已退出登录", null);
    }

    /**
     * 获取当前用户信息
     * GET /api/users/me
     */
    @GetMapping("/api/users/me")
    public Result<UserProfileDTO> getCurrentUser(@CurrentUser Long userId) {
        log.debug("获取当前用户信息: userId={}", userId);
        UserProfileDTO profile = queryService.getUserProfile(userId);
        return Result.success(profile);
    }

    /**
     * 更新个人资料
     * PUT /api/users/me/profile
     */
    @PutMapping("/api/users/me/profile")
    public Result<UserProfileDTO> updateProfile(
            @CurrentUser Long userId,
            @Valid @RequestBody ProfileUpdateRequest request) {
        log.info("更新用户资料: userId={}, nickname={}", userId, request.nickname());
        UserProfileDTO profile = queryService.updateProfile(
                userId,
                request.nickname(),
                request.avatar()
        );
        return Result.success("个人资料更新成功", profile);
    }

    /**
     * 修改密码
     * PUT /api/users/me/password
     */
    @PutMapping("/api/users/me/password")
    public Result<Void> changePassword(
            @CurrentUser Long userId,
            @Valid @RequestBody PasswordChangeRequest request) {
        log.info("修改用户密码: userId={}", userId);
        queryService.updatePassword(userId, request.oldPassword(), request.newPassword());
        return Result.success("密码修改成功", null);
    }
}

package interview.guide.modules.user.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.result.Result;
import interview.guide.modules.user.dto.LoginRequest;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.PasswordChangeRequest;
import interview.guide.modules.user.dto.ProfileUpdateRequest;
import interview.guide.modules.user.dto.RegisterRequest;
import interview.guide.modules.user.dto.RegisterResponse;
import interview.guide.modules.user.model.UserProfileDTO;
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

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/api/auth/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("收到用户注册请求: username={}", request.username());
        RegisterResponse response = registerService.register(request);
        return Result.success("注册成功", response);
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/api/auth/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("收到用户登录请求: username={}", request.username());
        LoginResponse response = loginService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * H5测试用登录接口 - 自动创建用户
     * POST /api/auth/login/test
     */
    @PostMapping("/api/auth/login/test")
    public Result<LoginResponse> testLogin(@RequestBody LoginRequest request) {
        log.info("收到H5测试登录请求: username={}", request.username());
        try {
            // 尝试登录
            LoginResponse response = loginService.login(request);
            return Result.success("登录成功", response);
        } catch (Exception e) {
            // 登录失败，尝试注册新用户
            log.info("用户不存在，尝试创建新用户: username={}", request.username());
            try {
                RegisterRequest registerRequest = new RegisterRequest(
                    request.username(),
                    request.password(),
                    request.username() + "@test.com",
                    request.username()
                );
                RegisterResponse registerResponse = registerService.register(registerRequest);
                // 注册成功后登录
                LoginResponse response = loginService.login(request);
                return Result.success("注册并登录成功", response);
            } catch (Exception ex) {
                log.error("H5测试登录失败", ex);
                return Result.error("登录失败: " + ex.getMessage());
            }
        }
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

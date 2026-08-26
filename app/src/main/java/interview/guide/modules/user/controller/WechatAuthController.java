package interview.guide.modules.user.controller;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.annotation.RateLimit.Dimension;
import interview.guide.common.annotation.RateLimit.TimeUnit;
import interview.guide.common.result.Result;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.WechatAuthDTOs.WechatBindByEmailRequest;
import interview.guide.modules.user.dto.WechatAuthDTOs.WechatBindByPasswordRequest;
import interview.guide.modules.user.dto.WechatAuthDTOs.WechatLoginResponse;
import interview.guide.modules.user.dto.WechatLoginRequest;
import interview.guide.modules.user.dto.WechatScanLoginRequest;
import interview.guide.modules.user.service.WechatAuthService;
import interview.guide.modules.user.service.WechatBindService;
import interview.guide.modules.user.service.WechatScanLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信授权控制器
 * 处理微信小程序和网页应用登录相关请求
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WechatAuthController {

    private final WechatAuthService wechatAuthService;
    private final WechatBindService wechatBindService;
    private final WechatScanLoginService wechatScanLoginService;

    /**
     * 微信小程序登录
     * POST /api/auth/wechat/login
     * 已绑定 → 返回登录态；未绑定 → 返回 needsBind=true + 关联票据
     */
    @PostMapping("/api/auth/wechat/login")
    @RateLimit(dimensions = {Dimension.IP},
            count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<WechatLoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        log.info("收到微信小程序登录请求");
        WechatLoginResponse response = wechatAuthService.wechatLogin(request);
        return Result.success(response.needsBind() ? "该微信未绑定账号" : "登录成功", response);
    }

    /**
     * 微信绑定-账号/邮箱+密码
     * POST /api/auth/wechat/bind/password
     */
    @PostMapping("/api/auth/wechat/bind/password")
    @RateLimit(dimensions = {Dimension.IP},
            count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<LoginResponse> bindByPassword(@Valid @RequestBody WechatBindByPasswordRequest request) {
        log.info("收到微信绑定请求（密码通道）");
        LoginResponse response = wechatBindService.bindByPassword(request);
        return Result.success("绑定成功", response);
    }

    /**
     * 微信绑定-邮箱+验证码
     * POST /api/auth/wechat/bind/email-code
     */
    @PostMapping("/api/auth/wechat/bind/email-code")
    @RateLimit(dimensions = {Dimension.IP},
            count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    public Result<LoginResponse> bindByEmailCode(@Valid @RequestBody WechatBindByEmailRequest request) {
        log.info("收到微信绑定请求（验证码通道）");
        LoginResponse response = wechatBindService.bindByEmailCode(request);
        return Result.success("绑定成功", response);
    }

    /**
     * 微信网页扫码登录
     * POST /api/auth/wechat/scan/login
     *
     * @param request 微信扫码登录请求，仅包含code
     * @return 登录结果，包含JWT token和用户信息
     */
    @PostMapping("/api/auth/wechat/scan/login")
    public Result<LoginResponse> wechatScanLogin(@Valid @RequestBody WechatScanLoginRequest request) {
        log.info("收到微信网页扫码登录请求");
        LoginResponse response = wechatScanLoginService.scanLogin(request.code());
        return Result.success("登录成功", response);
    }
}

package interview.guide.modules.user.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.WechatLoginRequest;
import interview.guide.modules.user.dto.WechatScanLoginRequest;
import interview.guide.modules.user.service.WechatAuthService;
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
    private final WechatScanLoginService wechatScanLoginService;

    /**
     * 微信小程序登录
     * POST /api/auth/wechat/login
     *
     * @param request 微信登录请求，包含code、encryptedData、iv等
     * @return 登录结果，包含JWT token和用户信息
     */
    @PostMapping("/api/auth/wechat/login")
    public Result<LoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        log.info("收到微信小程序登录请求");
        LoginResponse response = wechatAuthService.wechatLogin(request);
        return Result.success("登录成功", response);
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

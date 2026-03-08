package interview.guide.modules.config.controller;

import interview.guide.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 会话配置控制器
 */
@RestController
@RequestMapping("/api/config")
public class SessionConfigController {

    /**
     * 会话空闲超时时间（分钟）
     */
    @Value("${app.session.idle-timeout-minutes:30}")
    private int sessionIdleTimeoutMinutes;

    /**
     * Token 过期时间（小时）
     */
    @Value("${app.jwt.token-expiration-hours:24}")
    private int tokenExpirationHours;

    /**
     * 获取会话配置
     */
    @GetMapping("/session")
    public Result<Map<String, Object>> getSessionConfig() {
        return Result.success(Map.of(
                "idleTimeoutMinutes", sessionIdleTimeoutMinutes,
                "tokenExpirationHours", tokenExpirationHours
        ));
    }
}

package interview.guide.modules.admin.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.admin.model.SystemConfigEntity;
import interview.guide.modules.admin.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    /**
     * 获取所有系统配置
     * GET /api/admin/config
     */
    @GetMapping
    public Result<List<SystemConfigEntity>> getAllConfig() {
        log.info("获取所有系统配置");
        List<SystemConfigEntity> configs = configService.getAllConfig();
        return Result.success(configs);
    }

    /**
     * 根据key获取配置
     * GET /api/admin/config/{key}
     */
    @GetMapping("/{key}")
    public Result<SystemConfigEntity> getConfig(@PathVariable String key) {
        log.info("获取系统配置: key={}", key);
        SystemConfigEntity config = configService.getConfigByKey(key);
        return Result.success(config);
    }

    /**
     * 更新配置
     * PUT /api/admin/config/{key}
     */
    @PutMapping("/{key}")
    public Result<Void> updateConfig(@PathVariable String key, @RequestBody Map<String, String> body) {
        String value = body.get("value");
        log.info("更新系统配置: key={}, value={}", key, value);
        configService.updateConfig(key, value);
        return Result.success("配置更新成功", null);
    }

    /**
     * 批量更新配置
     * PUT /api/admin/config
     */
    @PutMapping
    public Result<Void> updateConfigs(@RequestBody Map<String, String> configs) {
        log.info("批量更新系统配置: {} 条", configs.size());
        configService.updateConfigs(configs);
        return Result.success("配置批量更新成功", null);
    }

    /**
     * 初始化默认配置
     * POST /api/admin/config/init
     */
    @PostMapping("/init")
    public Result<Void> initDefaultConfigs() {
        log.info("初始化默认系统配置");
        configService.initDefaultConfigs();
        return Result.success("默认配置初始化成功", null);
    }
}

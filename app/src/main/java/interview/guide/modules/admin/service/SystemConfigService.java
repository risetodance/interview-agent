package interview.guide.modules.admin.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.admin.model.SystemConfigEntity;
import interview.guide.modules.admin.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    /**
     * 获取所有系统配置
     */
    public List<SystemConfigEntity> getAllConfig() {
        return configRepository.findAll();
    }

    /**
     * 根据key获取配置
     */
    public SystemConfigEntity getConfigByKey(String key) {
        return configRepository.findByConfigKey(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFIG_NOT_FOUND));
    }

    /**
     * 获取配置值
     */
    public String getConfigValue(String key) {
        return configRepository.findByConfigKey(key)
                .map(SystemConfigEntity::getConfigValue)
                .orElse(null);
    }

    /**
     * 批量获取配置
     */
    public Map<String, String> getConfigs(List<String> keys) {
        List<SystemConfigEntity> configs = configRepository.findByConfigKeyIn(keys);
        return configs.stream()
                .collect(Collectors.toMap(
                        SystemConfigEntity::getConfigKey,
                        SystemConfigEntity::getConfigValue
                ));
    }

    /**
     * 更新配置
     */
    @Transactional
    public void updateConfig(String key, String value) {
        SystemConfigEntity config = configRepository.findByConfigKey(key)
                .orElseGet(() -> {
                    // 如果不存在，则创建新配置
                    SystemConfigEntity newConfig = new SystemConfigEntity();
                    newConfig.setConfigKey(key);
                    return newConfig;
                });
        config.setConfigValue(value);
        configRepository.save(config);
        log.info("更新系统配置: key={}, value={}", key, value);
    }

    /**
     * 批量更新配置
     */
    @Transactional
    public void updateConfigs(Map<String, String> configs) {
        configs.forEach(this::updateConfig);
        log.info("批量更新系统配置: {} 条", configs.size());
    }

    /**
     * 初始化默认配置
     */
    @Transactional
    public void initDefaultConfigs() {
        // AI模型配置
        createConfigIfNotExists("ai.model", "qwen-plus", "AI模型");
        createConfigIfNotExists("ai.temperature", "0.7", "AI温度参数");
        createConfigIfNotExists("ai.maxTokens", "2000", "AI最大token数");

        // 限流配置
        createConfigIfNotExists("rateLimit.global.enabled", "true", "全局限流开关");
        createConfigIfNotExists("rateLimit.global.max", "100", "全局限流次数");
        createConfigIfNotExists("rateLimit.global.ttl", "60", "全局限流时间(秒)");

        // 积分配置
        createConfigIfNotExists("points.signIn", "10", "签到获得积分");
        createConfigIfNotExists("points.interview", "50", "完成面试获得积分");

        // 会员配额配置
        createConfigIfNotExists("quota.free.resume", "3", "免费用户简历额度");
        createConfigIfNotExists("quota.free.interview", "5", "免费用户面试额度");
        createConfigIfNotExists("quota.vip.resume", "100", "VIP用户简历额度");
        createConfigIfNotExists("quota.vip.interview", "500", "VIP用户面试额度");

        log.info("初始化默认系统配置完成");
    }

    private void createConfigIfNotExists(String key, String defaultValue, String description) {
        if (configRepository.findByConfigKey(key).isEmpty()) {
            SystemConfigEntity config = new SystemConfigEntity();
            config.setConfigKey(key);
            config.setConfigValue(defaultValue);
            config.setDescription(description);
            configRepository.save(config);
        }
    }
}

package interview.guide.modules.notification.service;

import interview.guide.modules.notification.model.UserNotificationSettingsDTO;
import interview.guide.modules.notification.model.UserNotificationSettingsEntity;
import interview.guide.modules.notification.repository.UserNotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户通知设置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final UserNotificationSettingsRepository settingsRepository;

    /**
     * 获取用户通知设置（如果不存在则创建默认设置）
     */
    public UserNotificationSettingsDTO getSettings(Long userId) {
        UserNotificationSettingsEntity settings = settingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
        return toDTO(settings);
    }

    /**
     * 创建默认通知设置
     */
    private UserNotificationSettingsEntity createDefaultSettings(Long userId) {
        UserNotificationSettingsEntity settings = UserNotificationSettingsEntity.builder()
                .userId(userId)
                .inAppEnabled(true)
                .emailEnabled(false)
                .smsEnabled(false)
                .wechatEnabled(false)
                .build();
        return settingsRepository.save(settings);
    }

    /**
     * 更新用户通知设置
     */
    @Transactional
    public UserNotificationSettingsDTO updateSettings(Long userId, Boolean inAppEnabled, Boolean emailEnabled,
                                                       Boolean smsEnabled, Boolean wechatEnabled) {
        UserNotificationSettingsEntity settings = settingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));

        if (inAppEnabled != null) {
            settings.setInAppEnabled(inAppEnabled);
        }
        if (emailEnabled != null) {
            settings.setEmailEnabled(emailEnabled);
        }
        if (smsEnabled != null) {
            settings.setSmsEnabled(smsEnabled);
        }
        if (wechatEnabled != null) {
            settings.setWechatEnabled(wechatEnabled);
        }

        UserNotificationSettingsEntity saved = settingsRepository.save(settings);
        log.info("更新用户通知设置: userId={}, inApp={}, email={}, sms={}, wechat={}",
                userId, inAppEnabled, emailEnabled, smsEnabled, wechatEnabled);
        return toDTO(saved);
    }

    /**
     * 检查用户是否开启了指定渠道的通知
     */
    public boolean isChannelEnabled(Long userId, String channel) {
        UserNotificationSettingsDTO settings = getSettings(userId);
        return switch (channel.toUpperCase()) {
            case "IN_APP" -> settings.getInAppEnabled();
            case "EMAIL" -> settings.getEmailEnabled();
            case "SMS" -> settings.getSmsEnabled();
            case "WECHAT" -> settings.getWechatEnabled();
            default -> false;
        };
    }

    /**
     * 实体转DTO
     */
    private UserNotificationSettingsDTO toDTO(UserNotificationSettingsEntity entity) {
        return UserNotificationSettingsDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .inAppEnabled(entity.getInAppEnabled())
                .emailEnabled(entity.getEmailEnabled())
                .smsEnabled(entity.getSmsEnabled())
                .wechatEnabled(entity.getWechatEnabled())
                .build();
    }
}

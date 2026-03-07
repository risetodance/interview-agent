package interview.guide.modules.notification.service;

import interview.guide.modules.notification.enums.NotificationChannel;
import interview.guide.modules.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知发送服务
 * 根据用户设置决定是否发送通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSenderService {

    private final NotificationService notificationService;
    private final NotificationSettingsService notificationSettingsService;

    /**
     * 发送通知（根据用户设置决定是否发送）
     *
     * @param userId    用户ID
     * @param type      通知类型
     * @param channel   通知渠道
     * @param title     标题（可选，为null时使用默认标题）
     * @param content   内容（可选，为null时使用默认内容）
     * @param relatedId 关联业务ID
     * @param relatedType 关联业务类型
     */
    public void sendNotification(Long userId, NotificationType type, NotificationChannel channel,
                                 String title, String content, Long relatedId, String relatedType) {
        // 检查用户是否开启了该渠道的通知
        boolean channelEnabled = notificationSettingsService.isChannelEnabled(userId, channel.name());

        if (!channelEnabled) {
            log.debug("用户未开启 {} 通知渠道，跳过发送: userId={}", channel, userId);
            return;
        }

        // 发送通知
        notificationService.createNotification(userId, type, channel, title, content, relatedId, relatedType);
        log.info("通知已发送: userId={}, type={}, channel={}", userId, type, channel);
    }

    /**
     * 发送站内通知
     */
    public void sendInAppNotification(Long userId, NotificationType type, Long relatedId, String relatedType) {
        sendNotification(userId, type, NotificationChannel.IN_APP, null, null, relatedId, relatedType);
    }

    /**
     * 发送站内通知（自定义标题和内容）
     */
    public void sendInAppNotification(Long userId, NotificationType type, String title, String content,
                                      Long relatedId, String relatedType) {
        sendNotification(userId, type, NotificationChannel.IN_APP, title, content, relatedId, relatedType);
    }

    /**
     * 发送邮件通知
     */
    public void sendEmailNotification(Long userId, NotificationType type, String title, String content,
                                      Long relatedId, String relatedType) {
        sendNotification(userId, type, NotificationChannel.EMAIL, title, content, relatedId, relatedType);
    }

    /**
     * 发送短信通知
     */
    public void sendSmsNotification(Long userId, NotificationType type, String title, String content,
                                     Long relatedId, String relatedType) {
        sendNotification(userId, type, NotificationChannel.SMS, title, content, relatedId, relatedType);
    }

    /**
     * 发送微信通知
     */
    public void sendWechatNotification(Long userId, NotificationType type, String title, String content,
                                        Long relatedId, String relatedType) {
        sendNotification(userId, type, NotificationChannel.WECHAT, title, content, relatedId, relatedType);
    }
}

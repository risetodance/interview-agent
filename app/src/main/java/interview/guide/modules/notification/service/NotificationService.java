package interview.guide.modules.notification.service;

import interview.guide.modules.notification.enums.NotificationChannel;
import interview.guide.modules.notification.enums.NotificationType;
import interview.guide.modules.notification.model.NotificationDTO;
import interview.guide.modules.notification.model.NotificationEntity;
import interview.guide.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 通知服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 创建通知
     */
    @Transactional
    public NotificationDTO createNotification(Long userId, NotificationType type, NotificationChannel channel,
                                               String title, String content, Long relatedId, String relatedType) {
        NotificationEntity entity = NotificationEntity.builder()
                .userId(userId)
                .type(type)
                .channel(channel)
                .title(title != null ? title : type.getTitle())
                .content(content != null ? content : type.getDefaultContent())
                .isRead(false)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .build();

        NotificationEntity saved = notificationRepository.save(entity);
        log.info("创建通知成功: userId={}, type={}, channel={}", userId, type, channel);
        return toDTO(saved);
    }

    /**
     * 简化创建通知（使用默认标题和内容）
     */
    @Transactional
    public NotificationDTO createNotification(Long userId, NotificationType type, NotificationChannel channel) {
        return createNotification(userId, type, channel, null, null, null, null);
    }

    /**
     * 创建带业务关联的通知
     */
    @Transactional
    public NotificationDTO createNotification(Long userId, NotificationType type, NotificationChannel channel,
                                               Long relatedId, String relatedType) {
        return createNotification(userId, type, channel, null, null, relatedId, relatedType);
    }

    /**
     * 分页查询用户通知
     */
    public Page<NotificationDTO> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDTO);
    }

    /**
     * 分页查询用户未读通知
     */
    public Page<NotificationDTO> getUnreadNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDTO);
    }

    /**
     * 统计用户未读通知数量
     */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * 标记单条通知为已读
     */
    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {
        Optional<NotificationEntity> notification = notificationRepository.findByIdAndUserId(notificationId, userId);
        if (notification.isEmpty()) {
            log.warn("通知不存在或不属于该用户: notificationId={}, userId={}", notificationId, userId);
            return false;
        }

        int updated = notificationRepository.markAsReadByIdAndUserId(notificationId, userId, LocalDateTime.now());
        if (updated > 0) {
            log.info("标记通知为已读: notificationId={}, userId={}", notificationId, userId);
        }
        return updated > 0;
    }

    /**
     * 标记用户所有通知为已读
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        int count = notificationRepository.markAllAsReadByUserId(userId, LocalDateTime.now());
        log.info("标记所有通知为已读: userId={}, count={}", userId, count);
        return count;
    }

    /**
     * 实体转DTO
     */
    private NotificationDTO toDTO(NotificationEntity entity) {
        return NotificationDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .type(entity.getType())
                .channel(entity.getChannel())
                .title(entity.getTitle())
                .content(entity.getContent())
                .isRead(entity.getIsRead())
                .relatedId(entity.getRelatedId())
                .relatedType(entity.getRelatedType())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .build();
    }
}

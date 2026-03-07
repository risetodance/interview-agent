package interview.guide.modules.notification.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.result.Result;
import interview.guide.modules.notification.model.NotificationDTO;
import interview.guide.modules.notification.model.UserNotificationSettingsDTO;
import interview.guide.modules.notification.service.NotificationService;
import interview.guide.modules.notification.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * 通知控制器
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingsService notificationSettingsService;

    /**
     * 获取通知列表
     */
    @GetMapping
    public Result<Page<NotificationDTO>> getNotifications(
            @CurrentUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationDTO> notifications = notificationService.getNotifications(userId, pageable);
        return Result.success(notifications);
    }

    /**
     * 获取未读通知列表
     */
    @GetMapping("/unread")
    public Result<Page<NotificationDTO>> getUnreadNotifications(
            @CurrentUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationDTO> notifications = notificationService.getUnreadNotifications(userId, pageable);
        return Result.success(notifications);
    }

    /**
     * 获取未读通知数量
     */
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount(@CurrentUser Long userId) {
        long count = notificationService.getUnreadCount(userId);
        return Result.success(count);
    }

    /**
     * 标记通知为已读
     */
    @PutMapping("/{id}/read")
    public Result<Boolean> markAsRead(
            @PathVariable Long id,
            @CurrentUser Long userId) {
        boolean success = notificationService.markAsRead(id, userId);
        return Result.success(success);
    }

    /**
     * 标记所有通知为已读
     */
    @PutMapping("/read-all")
    public Result<Integer> markAllAsRead(@CurrentUser Long userId) {
        int count = notificationService.markAllAsRead(userId);
        return Result.success(count);
    }

    /**
     * 获取用户通知设置
     */
    @GetMapping("/settings")
    public Result<UserNotificationSettingsDTO> getSettings(@CurrentUser Long userId) {
        UserNotificationSettingsDTO settings = notificationSettingsService.getSettings(userId);
        return Result.success(settings);
    }

    /**
     * 更新用户通知设置
     */
    @PutMapping("/settings")
    public Result<UserNotificationSettingsDTO> updateSettings(
            @CurrentUser Long userId,
            @RequestParam(required = false) Boolean inAppEnabled,
            @RequestParam(required = false) Boolean emailEnabled,
            @RequestParam(required = false) Boolean smsEnabled,
            @RequestParam(required = false) Boolean wechatEnabled) {
        UserNotificationSettingsDTO settings = notificationSettingsService.updateSettings(
                userId, inAppEnabled, emailEnabled, smsEnabled, wechatEnabled);
        return Result.success(settings);
    }
}

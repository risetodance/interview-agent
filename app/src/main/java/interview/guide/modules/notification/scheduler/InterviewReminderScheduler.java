package interview.guide.modules.notification.scheduler;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.notification.enums.NotificationChannel;
import interview.guide.modules.notification.enums.NotificationType;
import interview.guide.modules.notification.service.NotificationSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试提醒定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewReminderScheduler {

    private final InterviewSessionRepository interviewSessionRepository;
    private final NotificationSenderService notificationSenderService;

    /**
     * 提醒提前时间（分钟）
     */
    private static final int REMINDER_MINUTES_BEFORE = 30;

    /**
     * 每分钟执行一次，检查需要提醒的面试
     */
    @Scheduled(fixedRate = 60000)
    public void sendInterviewReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderTime = now.plusMinutes(REMINDER_MINUTES_BEFORE);

        // 查询即将开始的面试（在提醒时间范围内且未发送过提醒的）
        List<InterviewSessionEntity> upcomingInterviews = interviewSessionRepository
                .findByStatusAndScheduledTimeBefore(InterviewSessionEntity.SessionStatus.CREATED, reminderTime);

        for (InterviewSessionEntity interview : upcomingInterviews) {
            try {
                Long userId = interview.getResume().getUserId();
                Long interviewId = interview.getId();

                // 检查是否已发送过提醒（通过查询通知表，这里简化处理，使用 interview 的提醒标记字段）
                if (!Boolean.TRUE.equals(interview.getReminderSent())) {
                    // 发送面试提醒
                    String title = "面试即将开始";
                    String content = String.format("您的面试将在 %d 分钟后开始，请做好准备。",
                            REMINDER_MINUTES_BEFORE);

                    notificationSenderService.sendInAppNotification(
                            userId,
                            NotificationType.INTERVIEW_REMINDER,
                            title,
                            content,
                            interviewId,
                            "INTERVIEW"
                    );

                    // 标记已发送提醒
                    interview.setReminderSent(true);
                    interviewSessionRepository.save(interview);

                    log.info("面试提醒已发送: interviewId={}, userId={}", interviewId, userId);
                }
            } catch (Exception e) {
                log.error("发送面试提醒失败: interviewId={}, error={}", interview.getId(), e.getMessage(), e);
            }
        }
    }
}

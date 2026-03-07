package interview.guide.modules.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知类型枚举
 */
@Getter
@AllArgsConstructor
public enum NotificationType {
    /**
     * 简历分析完成
     */
    RESUME_COMPLETE("简历分析完成", "您的简历分析已完成"),
    /**
     * 知识库向量化完成
     */
    KB_COMPLETE("知识库处理完成", "您的知识库文档处理已完成"),
    /**
     * 面试提醒
     */
    INTERVIEW_REMINDER("面试提醒", "您有一个即将开始的面试"),
    /**
     * 系统通知
     */
    SYSTEM("系统通知", "系统通知");

    private final String title;
    private final String defaultContent;
}

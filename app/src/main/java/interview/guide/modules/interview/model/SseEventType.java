package interview.guide.modules.interview.model;

/**
 * SSE 事件类型枚举
 */
public enum SseEventType {

    // 连接成功
    CONNECTED("connected"),
    // 心跳
    PING("ping"),
    // 进度状态 - 评分中
    PROGRESS_SCORING("progress_scoring"),
    // 进度状态 - 决策中
    PROGRESS_DECIDING("progress_deciding"),
    // 进度状态 - 搜索准备中
    PROGRESS_SEARCH_PREPARING("progress_search_preparing"),
    // 进度状态 - 生成问题中
    PROGRESS_GENERATING("progress_generating"),
    // 问题推送
    QUESTION("question"),
    // 面试完成
    INTERVIEW_COMPLETE("interview_complete"),
    // 错误
    ERROR("error");

    private final String eventName;

    SseEventType(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }
}
package interview.guide.modules.interview.model;

import java.util.List;

/**
 * 会话进度DTO
 * 用于继续面试场景，返回会话进度和历史答题记录
 */
public record SessionProgressDTO(
    String sessionId,
    int currentQuestionIndex,
    int totalQuestions,
    CurrentQuestionDTO currentQuestion,
    List<AnswerHistoryDTO> history,
    ProcessingStatus processingStatus,  // 处理状态
    String sessionStatus  // 会话状态：CREATED/IN_PROGRESS/COMPLETED/EVALUATED，前端据此判断面试是否结束
) {
    /**
     * 处理状态枚举
     */
    public enum ProcessingStatus {
        IDLE,       // 空闲，可以继续答题
        PROCESSING  // 工作流正在处理中
    }

    /**
     * 便捷构造方法（向后兼容）
     */
    public SessionProgressDTO(String sessionId, int currentQuestionIndex, int totalQuestions,
                              CurrentQuestionDTO currentQuestion, List<AnswerHistoryDTO> history) {
        this(sessionId, currentQuestionIndex, totalQuestions, currentQuestion, history, ProcessingStatus.IDLE, null);
    }
}

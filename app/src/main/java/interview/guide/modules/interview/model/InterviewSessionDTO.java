package interview.guide.modules.interview.model;

import java.util.List;

/**
 * 面试会话DTO
 */
public record InterviewSessionDTO(
    String sessionId,
    String resumeText,
    int totalQuestions,
    int currentQuestionIndex,
    List<InterviewQuestionDTO> questions,
    SessionStatus status,
    List<Long> knowledgeBaseIds  // 关联的知识库ID列表
) {
    public InterviewSessionDTO(String sessionId, String resumeText, int totalQuestions,
                              int currentQuestionIndex, List<InterviewQuestionDTO> questions,
                              SessionStatus status) {
        this(sessionId, resumeText, totalQuestions, currentQuestionIndex, questions, status, null);
    }

    public enum SessionStatus {
        CREATED,      // 会话已创建
        IN_PROGRESS,  // 面试进行中
        COMPLETED,    // 面试已完成
        EVALUATED     // 已生成评估报告
    }
}

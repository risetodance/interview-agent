package interview.guide.modules.interview.model;

/**
 * 面试会话基本信息DTO（不包含问题列表）
 * 用于列表和详情接口，前端应通过 /current 接口获取当前问题
 */
public record InterviewSessionBasicDTO(
    String sessionId,
    String resumeText,
    int totalQuestions,
    int currentQuestionIndex,
    String status,
    String currentDifficulty,
    Integer overallScore,
    Integer questionsGenerated,
    int answeredCount  // 已答题数量
) {}

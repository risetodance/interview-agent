package interview.guide.modules.interview.model;

/**
 * 面试评估状态DTO（轻量级）
 */
public record InterviewEvaluateStatusDTO(
    String sessionId,
    Integer overallScore,
    String evaluateStatus
) {}

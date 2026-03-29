package interview.guide.modules.interview.model;

/**
 * 答题历史DTO
 * 包含题目、用户答案和AI评分
 */
public record AnswerHistoryDTO(
    int questionIndex,
    String question,
    String category,
    String difficulty,
    String userAnswer,
    Integer score,
    String feedback,
    // 出题视角ID
    Long createdByPerspectiveId,
    // 出题视角名称
    String createdByPerspectiveName,
    // 追问相关
    Boolean isFollowUp,
    Integer relatedIndex,
    String relatedQuestion
) {}

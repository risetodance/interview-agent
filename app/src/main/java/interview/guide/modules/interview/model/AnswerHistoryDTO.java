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
    String feedback
) {}

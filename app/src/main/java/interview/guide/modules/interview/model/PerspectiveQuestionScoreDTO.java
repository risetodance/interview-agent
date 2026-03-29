package interview.guide.modules.interview.model;

import java.util.List;

/**
 * 视角下的问题评分 DTO
 */
public record PerspectiveQuestionScoreDTO(
    Integer questionIndex,
    Integer score,
    String feedback,
    String question,
    String userAnswer,
    String referenceAnswer,
    List<String> keyPoints
) {}

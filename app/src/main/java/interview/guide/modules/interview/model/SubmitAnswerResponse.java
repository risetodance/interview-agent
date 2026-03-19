package interview.guide.modules.interview.model;

import java.util.Map;

/**
 * 提交答案响应（自适应难度版本）
 */
public record SubmitAnswerResponse(
    boolean hasNextQuestion,
    CurrentQuestionDTO nextQuestion,
    int newIndex,
    int questionsGenerated,
    int currentScore,
    Map<String, CategoryScoreDTO> categoryScores,
    String nextDifficulty
) {}

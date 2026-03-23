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
    List<AnswerHistoryDTO> history
) {}

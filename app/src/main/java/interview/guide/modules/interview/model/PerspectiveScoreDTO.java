package interview.guide.modules.interview.model;

import java.util.List;

/**
 * 视角评分 DTO
 */
public record PerspectiveScoreDTO(
    Long id,
    Long sessionId,
    Long perspectiveId,
    String perspectiveName,
    String perspectiveIcon,
    Integer questionIndex,
    Integer score,
    String feedback,
    List<String> strengths,
    List<String> improvements,
    String status,
    String errorMessage,
    String completedAt,
    String createdAt
) {}

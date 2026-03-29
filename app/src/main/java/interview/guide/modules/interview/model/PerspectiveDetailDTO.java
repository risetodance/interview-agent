package interview.guide.modules.interview.model;

import java.util.List;

/**
 * 视角详情 DTO（用于综合报告中的视角详情切换）
 */
public record PerspectiveDetailDTO(
    Long perspectiveId,
    String roleName,
    String perspectiveIcon,
    Integer score,
    String feedback,
    List<String> strengths,
    List<String> improvements,
    List<PerspectiveQuestionScoreDTO> questionScores
) {}

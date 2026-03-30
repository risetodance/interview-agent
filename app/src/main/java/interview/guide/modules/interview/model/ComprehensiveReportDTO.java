package interview.guide.modules.interview.model;

import java.util.List;
import java.util.Map;

/**
 * 综合报告 DTO
 */
public record ComprehensiveReportDTO(
    String sessionId,
    Integer overallScore,
    List<PerspectiveScoreDTO> perspectives,
    String evaluation,
    List<String> strengths,
    List<String> improvements,
    String developmentSuggestions,
    Map<String, PerspectiveDetailDTO> perspectiveDetails
) {}

package interview.guide.modules.interview.model;

/**
 * 面试官角色 DTO
 */
public record InterviewerRoleDTO(
    Long id,
    String roleName,
    String roleCode,
    String description,
    String questionPrompt,
    String scoringPrompt,
    Double weight,
    String icon,
    Integer sortOrder,
    Boolean status,
    Boolean defaultTemplate,
    String createdAt,
    String updatedAt
) {}

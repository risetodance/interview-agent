package interview.guide.modules.interview.model;

import jakarta.validation.constraints.*;

/**
 * 创建/更新面试官角色请求
 */
public record CreateInterviewerRoleRequest(
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称最多100个字符")
    String roleName,

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码最多50个字符")
    @Pattern(regexp = "^[A-Z_]+$", message = "角色编码只能包含大写字母和下划线")
    String roleCode,

    String description,

    @NotBlank(message = "出题Prompt不能为空")
    String questionPrompt,

    @NotBlank(message = "评分Prompt不能为空")
    String scoringPrompt,

    @DecimalMin(value = "0.0", message = "权重最小为0")
    @DecimalMax(value = "1.0", message = "权重最大为1.0")
    Double weight,

    @Size(max = 50, message = "图标最多50个字符")
    String icon,

    Integer sortOrder,

    Boolean status,

    Boolean defaultTemplate
) {}

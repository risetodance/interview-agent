package interview.guide.modules.interview.model;

import interview.guide.common.annotation.PerspectiveWeightsValid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

/**
 * 创建面试会话请求（扩展版本，支持多视角）
 */
public record CreateSessionRequest(
    String resumeText,

    @Min(value = 3, message = "题目数量最少3题")
    @Max(value = 20, message = "题目数量最多20题")
    int questionCount,

    Long resumeId,

    Boolean forceCreate,

    List<Long> questionBankIds,

    List<Long> knowledgeBaseIds,

    /**
     * 选择的视角ID列表（最多3个）
     */
    @Size(max = 3, message = "最多选择3个视角")
    List<Long> selectedPerspectives,

    /**
     * 各视角的权重配置（Map: perspectiveId -> weight）
     * 如果不指定，则使用视角角色表中的默认权重
     */
    @PerspectiveWeightsValid
    Map<Long, Double> perspectiveWeights
) {}

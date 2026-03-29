package interview.guide.modules.interview.model;

import interview.guide.common.annotation.PerspectiveWeightsValid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

/**
 * 创建面试会话请求
 */
public record CreateInterviewRequest(
    String resumeText,      // 简历文本内容（可选）

    @Min(value = 3, message = "题目数量最少3题")
    @Max(value = 20, message = "题目数量最多20题")
    int questionCount,      // 面试题目数量 (3-20)

    Long resumeId,          // 简历ID（可选，用于持久化关联）

    Boolean forceCreate,    // 是否强制创建新会话（忽略未完成的会话），默认为 false

    List<Long> questionBankIds,  // 指定的题库ID列表（可选，不指定则从所有题库随机）

    List<Long> knowledgeBaseIds,  // 知识库ID列表（可选，用于生成面试问题时参考知识库内容）

    /**
     * 选择的视角ID列表（最多3个，用于多视角面试）
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

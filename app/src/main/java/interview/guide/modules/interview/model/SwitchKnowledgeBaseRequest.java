package interview.guide.modules.interview.model;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 切换面试知识库请求
 */
public record SwitchKnowledgeBaseRequest(
    @NotEmpty(message = "知识库ID列表不能为空")
    List<Long> knowledgeBaseIds  // 新的知识库ID列表
) {}

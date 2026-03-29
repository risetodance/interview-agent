package interview.guide.modules.interview.model;

/**
 * 当前问题 DTO
 * 用于返回当前面试问题及其相关信息
 */
public record CurrentQuestionDTO(
        Integer questionIndex,
        String question,
        String category,
        String difficulty,
        Long knowledgeBaseId,
        String knowledgeBaseName,
        String referenceContext,
        Boolean isFollowUp,
        Integer relatedIndex,
        String relatedQuestion,
        // 出题视角ID
        Long createdByPerspectiveId,
        // 出题视角名称（如"技术面试官"）
        String createdByPerspectiveName
) {}

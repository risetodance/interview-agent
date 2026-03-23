package interview.guide.modules.interview.model;

/**
 * 面试问题DTO
 */
public record InterviewQuestionDTO(
    int questionIndex,
    String question,
    String category,      // 问题类别：由AI自由决定
    String userAnswer,    // 用户回答
    Integer score,        // 单题得分 (0-100)
    String feedback,      // 单题反馈
    boolean isFollowUp,   // 是否为追问
    Integer parentQuestionIndex // 追问关联的主问题索引
) {

    /**
     * 创建新问题（未回答状态）
     */
    public static InterviewQuestionDTO create(int index, String question, String category) {
        return new InterviewQuestionDTO(index, question, category, null, null, null, false, null);
    }

    /**
     * 创建新问题（支持追问标记）
     */
    public static InterviewQuestionDTO create(
            int index,
            String question,
            String category,
            boolean isFollowUp,
            Integer parentQuestionIndex) {
        return new InterviewQuestionDTO(index, question, category, null, null, null, isFollowUp, parentQuestionIndex);
    }

    /**
     * 添加用户回答
     */
    public InterviewQuestionDTO withAnswer(String answer) {
        return new InterviewQuestionDTO(
            questionIndex, question, category, answer, score, feedback, isFollowUp, parentQuestionIndex);
    }

    /**
     * 添加评分和反馈
     */
    public InterviewQuestionDTO withEvaluation(int score, String feedback) {
        return new InterviewQuestionDTO(
            questionIndex, question, category, userAnswer, score, feedback, isFollowUp, parentQuestionIndex);
    }
}

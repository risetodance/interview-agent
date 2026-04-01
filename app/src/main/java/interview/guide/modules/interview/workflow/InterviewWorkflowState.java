package interview.guide.modules.interview.workflow;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 面试工作流状态
 * 定义节点之间传递的状态
 */
@Data
public class InterviewWorkflowState {
    // 会话信息
    private String sessionId;
    private Long userId;

    // 当前问题上下文
    private Integer currentQuestionIndex;
    private String currentQuestion;
    private String currentCategory;
    private String currentDifficulty;
    private String currentAnswer;
    private Long perspectiveId;
    private String perspectiveName;

    // 历史记录（用于上下文）
    private List<AnswerHistory> history;

    // 决策结果
    private DecisionAction decisionAction;
    private String nextPerspectiveId;
    private String nextPerspectiveName;

    // 评估结果
    private Integer score;
    private String feedback;

    // 最终报告
    private Map<String, Object> finalReport;

    // 错误处理
    private String errorMessage;
    private boolean hasError;

    /**
     * 决策动作
     */
    public enum DecisionAction {
        ASK,      // 继续下一题
        SWITCH,  // 切换角色
        FINISH   // 结束面试
    }

    @Data
    public static class AnswerHistory {
        private Integer questionIndex;
        private String question;
        private String userAnswer;
        private String category;
        private String difficulty;
        private Integer score;
    }
}

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
    // ==================== 状态字段常量 ====================
    // 会话信息
    public static final String SESSION_ID = "sessionId";
    public static final String USER_ID = "userId";

    // 当前问题上下文
    public static final String CURRENT_QUESTION_INDEX = "currentQuestionIndex";
    public static final String CURRENT_QUESTION = "currentQuestion";
    public static final String CURRENT_CATEGORY = "currentCategory";
    public static final String CURRENT_DIFFICULTY = "currentDifficulty";
    public static final String CURRENT_ANSWER = "currentAnswer";
    public static final String PERSPECTIVE_ID = "perspectiveId";
    public static final String PERSPECTIVE_NAME = "perspectiveName";
    public static final String CURRENT_PERSPECTIVE_ID = "currentPerspectiveId";
    public static final String CURRENT_PERSPECTIVE_NAME = "currentPerspectiveName";

    // 历史记录
    public static final String HISTORY = "history";

    // 决策结果
    public static final String DECISION_ACTION = "decisionAction";
    public static final String NEXT_QUESTION_INDEX = "nextQuestionIndex";
    public static final String NEXT_PERSPECTIVE_ID = "nextPerspectiveId";
    public static final String NEXT_PERSPECTIVE_NAME = "nextPerspectiveName";
    public static final String DECISION_REASON = "decisionReason";
    public static final String IS_COMPLETE = "isComplete";

    // 搜索结果（来自 MCP web_search）
    public static final String SEARCH_RESULT = "searchResult";
    public static final String SEARCH_ENABLED = "searchEnabled";
    public static final String SEARCH_KEYWORDS = "searchKeywords";
    public static final String SEARCH_DECISION_REASON = "searchDecisionReason";

    // 评估结果
    public static final String SCORE = "score";
    public static final String FEEDBACK = "feedback";
    public static final String ADJUSTED_DIFFICULTY = "adjustedDifficulty";

    // 追问相关
    public static final String IS_FOLLOW_UP = "isFollowUp";
    public static final String RELATED_INDEX = "relatedIndex";
    public static final String RELATED_QUESTION = "relatedQuestion";

    // 知识库相关
    public static final String KNOWLEDGE_BASE_ID = "knowledgeBaseId";
    public static final String KNOWLEDGE_BASE_NAME = "knowledgeBaseName";
    public static final String CREATED_BY_PERSPECTIVE_ID = "createdByPerspectiveId";
    public static final String CREATED_BY_PERSPECTIVE_NAME = "createdByPerspectiveName";

    // 最终报告
    public static final String FINAL_REPORT = "finalReport";

    // 错误处理
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String HAS_ERROR = "hasError";

    // ==================== 状态字段 ====================
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
    private String nextPerspectiveId;
    private String nextPerspectiveName;

    // 搜索结果（来自 MCP web_search）
    private String searchResult;
    private Boolean searchEnabled;
    private String searchKeywords;

    // 评估结果
    private Integer score;
    private String feedback;

    // 最终报告
    private Map<String, Object> finalReport;

    // 错误处理
    private String errorMessage;
    private boolean hasError;

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

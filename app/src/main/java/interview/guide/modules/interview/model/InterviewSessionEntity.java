package interview.guide.modules.interview.model;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 面试会话实体
 */
@Entity
@Table(name = "interview_sessions", indexes = {
    @Index(name = "idx_interview_session_resume_created", columnList = "resume_id,created_at"),
    @Index(name = "idx_interview_session_resume_status_created", columnList = "resume_id,status,created_at")
})
public class InterviewSessionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 会话ID (UUID)
    @Column(nullable = false, unique = true, length = 36)
    private String sessionId;
    
    // 关联的简历（可选，用于通用面试模式）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = true)
    private ResumeEntity resume;
    
    // 问题总数
    private Integer totalQuestions;
    
    // 当前问题索引
    private Integer currentQuestionIndex = 0;
    
    // 会话状态
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SessionStatus status = SessionStatus.CREATED;
    
    // 问题列表 (JSON格式)
    @Column(columnDefinition = "TEXT")
    private String questionsJson;
    
    // 总分 (0-100)
    private Integer overallScore;
    
    // 总体评价
    @Column(columnDefinition = "TEXT")
    private String overallFeedback;
    
    // 优势 (JSON)
    @Column(columnDefinition = "TEXT")
    private String strengthsJson;
    
    // 改进建议 (JSON)
    @Column(columnDefinition = "TEXT")
    private String improvementsJson;
    
    // 参考答案 (JSON)
    @Column(columnDefinition = "TEXT")
    private String referenceAnswersJson;
    
    // 面试答案记录
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterviewAnswerEntity> answers = new ArrayList<>();
    
    // 创建时间
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    // 完成时间
    private LocalDateTime completedAt;

    // 评估状态（异步评估）
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AsyncTaskStatus evaluateStatus;

    // 评估错误信息
    @Column(length = 500)
    private String evaluateError;

    // 关联的知识库ID列表 (JSON格式, 存储如: [1, 2, 3])
    @Column(columnDefinition = "TEXT")
    private String knowledgeBaseIds;

    // 评分历史 (JSON格式, 存储如: [{"score": 85, "createdAt": "2024-01-01T10:00:00"}])
    @Column(columnDefinition = "TEXT")
    private String scoreHistory;

    // 预定面试时间（用于提醒）
    private LocalDateTime scheduledTime;

    // 提醒是否已发送
    private Boolean reminderSent = false;

    // 当前难度等级 (BASIC, ADVANCED, EXPERT)
    @Column(length = 20)
    private String currentDifficulty = "BASIC";

    // 分类得分 (JSON格式, 存储如: {"Java基础": {"totalScore": 80, "count": 2, "avgScore": 80}})
    @Column(columnDefinition = "TEXT")
    private String categoryScores;

    // 已生成的问题数量
    private Integer questionsGenerated = 0;

    // 选择的视角列表 (JSON数组，存储用户选择的视角ID列表)
    @Column(name = "selected_perspectives", columnDefinition = "TEXT")
    private String selectedPerspectives;

    // 汇总状态
    @Enumerated(EnumType.STRING)
    @Column(name = "perspective_summary_status", length = 20)
    private AsyncTaskStatus perspectiveSummaryStatus;

    // 综合得分（加权平均）
    @Column(name = "comprehensive_score")
    private Integer comprehensiveScore;

    // LLM生成的综合评价（评价）
    @Column(name = "comprehensive_feedback", columnDefinition = "TEXT")
    private String comprehensiveFeedback;

    // LLM生成的综合评价（发展建议）
    @Column(name = "development_suggestions", columnDefinition = "TEXT")
    private String developmentSuggestions;

    // 上一题由哪个视角出（用于轮询规则）
    @Column(name = "last_question_perspective_id")
    private Long lastQuestionPerspectiveId;

    // 各视角权重配置（JSON格式，存储如: {"1": 0.6, "2": 0.4}）
    @Column(name = "perspective_weights", columnDefinition = "TEXT")
    private String perspectiveWeights;

    // 用户ID（用于数据隔离）
    @Column(name = "user_id")
    private Long userId;

    public enum SessionStatus {
        CREATED,      // 会话已创建
        IN_PROGRESS,  // 面试进行中
        COMPLETED,    // 面试已完成
        EVALUATED     // 已生成评估报告
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public ResumeEntity getResume() {
        return resume;
    }
    
    public void setResume(ResumeEntity resume) {
        this.resume = resume;
    }
    
    public Integer getTotalQuestions() {
        return totalQuestions;
    }
    
    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }
    
    public Integer getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }
    
    public void setCurrentQuestionIndex(Integer currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }
    
    public SessionStatus getStatus() {
        return status;
    }
    
    public void setStatus(SessionStatus status) {
        this.status = status;
    }
    
    public String getQuestionsJson() {
        return questionsJson;
    }
    
    public void setQuestionsJson(String questionsJson) {
        this.questionsJson = questionsJson;
    }
    
    public Integer getOverallScore() {
        return overallScore;
    }
    
    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }
    
    public String getOverallFeedback() {
        return overallFeedback;
    }
    
    public void setOverallFeedback(String overallFeedback) {
        this.overallFeedback = overallFeedback;
    }
    
    public String getStrengthsJson() {
        return strengthsJson;
    }
    
    public void setStrengthsJson(String strengthsJson) {
        this.strengthsJson = strengthsJson;
    }
    
    public String getImprovementsJson() {
        return improvementsJson;
    }
    
    public void setImprovementsJson(String improvementsJson) {
        this.improvementsJson = improvementsJson;
    }
    
    public String getReferenceAnswersJson() {
        return referenceAnswersJson;
    }
    
    public void setReferenceAnswersJson(String referenceAnswersJson) {
        this.referenceAnswersJson = referenceAnswersJson;
    }
    
    public List<InterviewAnswerEntity> getAnswers() {
        return answers;
    }
    
    public void setAnswers(List<InterviewAnswerEntity> answers) {
        this.answers = answers;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public AsyncTaskStatus getEvaluateStatus() {
        return evaluateStatus;
    }

    public void setEvaluateStatus(AsyncTaskStatus evaluateStatus) {
        this.evaluateStatus = evaluateStatus;
    }

    public String getEvaluateError() {
        return evaluateError;
    }

    public void setEvaluateError(String evaluateError) {
        this.evaluateError = evaluateError;
    }

    public String getKnowledgeBaseIds() {
        return knowledgeBaseIds;
    }

    public void setKnowledgeBaseIds(String knowledgeBaseIds) {
        this.knowledgeBaseIds = knowledgeBaseIds;
    }

    public String getScoreHistory() {
        return scoreHistory;
    }

    public void setScoreHistory(String scoreHistory) {
        this.scoreHistory = scoreHistory;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public Boolean getReminderSent() {
        return reminderSent;
    }

    public void setReminderSent(Boolean reminderSent) {
        this.reminderSent = reminderSent;
    }

    public String getCurrentDifficulty() {
        return currentDifficulty;
    }

    public void setCurrentDifficulty(String currentDifficulty) {
        this.currentDifficulty = currentDifficulty;
    }

    public String getCategoryScores() {
        return categoryScores;
    }

    public void setCategoryScores(String categoryScores) {
        this.categoryScores = categoryScores;
    }

    public Integer getQuestionsGenerated() {
        return questionsGenerated;
    }

    public void setQuestionsGenerated(Integer questionsGenerated) {
        this.questionsGenerated = questionsGenerated;
    }

    public void addAnswer(InterviewAnswerEntity answer) {
        answers.add(answer);
        answer.setSession(this);
    }

    public String getSelectedPerspectives() {
        return selectedPerspectives;
    }

    public void setSelectedPerspectives(String selectedPerspectives) {
        this.selectedPerspectives = selectedPerspectives;
    }

    public AsyncTaskStatus getPerspectiveSummaryStatus() {
        return perspectiveSummaryStatus;
    }

    public void setPerspectiveSummaryStatus(AsyncTaskStatus perspectiveSummaryStatus) {
        this.perspectiveSummaryStatus = perspectiveSummaryStatus;
    }

    public Integer getComprehensiveScore() {
        return comprehensiveScore;
    }

    public void setComprehensiveScore(Integer comprehensiveScore) {
        this.comprehensiveScore = comprehensiveScore;
    }

    public String getComprehensiveFeedback() {
        return comprehensiveFeedback;
    }

    public void setComprehensiveFeedback(String comprehensiveFeedback) {
        this.comprehensiveFeedback = comprehensiveFeedback;
    }

    public String getDevelopmentSuggestions() {
        return developmentSuggestions;
    }

    public void setDevelopmentSuggestions(String developmentSuggestions) {
        this.developmentSuggestions = developmentSuggestions;
    }

    public Long getLastQuestionPerspectiveId() {
        return lastQuestionPerspectiveId;
    }

    public void setLastQuestionPerspectiveId(Long lastQuestionPerspectiveId) {
        this.lastQuestionPerspectiveId = lastQuestionPerspectiveId;
    }

    public String getPerspectiveWeights() {
        return perspectiveWeights;
    }

    public void setPerspectiveWeights(String perspectiveWeights) {
        this.perspectiveWeights = perspectiveWeights;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

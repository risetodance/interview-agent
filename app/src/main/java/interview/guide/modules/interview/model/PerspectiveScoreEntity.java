package interview.guide.modules.interview.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 各视角评分实体
 * 存储多视角面试中每个视角对答案/综合的独立评分
 */
@Entity
@Table(name = "perspective_scores", indexes = {
    @Index(name = "idx_perspective_scores_session", columnList = "session_id"),
    @Index(name = "idx_perspective_scores_perspective", columnList = "perspective_id"),
    @Index(name = "idx_perspective_scores_session_perspective", columnList = "session_id,perspective_id")
})
public class PerspectiveScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联面试会话
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    // 关联面试官角色
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perspective_id", nullable = false)
    private InterviewerRoleEntity perspective;

    // 问题索引（-1 表示综合评分）
    @Column(name = "question_index")
    private Integer questionIndex = -1;

    // 得分（0-100）
    private Integer score;

    // 评价内容
    @Column(columnDefinition = "TEXT")
    private String feedback;

    // 优势列表（JSON）
    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    // 改进建议（JSON）
    @Column(name = "improvements_json", columnDefinition = "TEXT")
    private String improvementsJson;

    // 评分状态
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PerspectiveScoreStatus status = PerspectiveScoreStatus.PENDING;

    // 错误信息
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // 完成时间
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 创建时间
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum PerspectiveScoreStatus {
        PENDING,     // 待处理
        PROCESSING,   // 处理中
        COMPLETED,    // 完成
        FAILED        // 失败
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public InterviewerRoleEntity getPerspective() {
        return perspective;
    }

    public void setPerspective(InterviewerRoleEntity perspective) {
        this.perspective = perspective;
    }

    public Integer getQuestionIndex() {
        return questionIndex;
    }

    public void setQuestionIndex(Integer questionIndex) {
        this.questionIndex = questionIndex;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
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

    public PerspectiveScoreStatus getStatus() {
        return status;
    }

    public void setStatus(PerspectiveScoreStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

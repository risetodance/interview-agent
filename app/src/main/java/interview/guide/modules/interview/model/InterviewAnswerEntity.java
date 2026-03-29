package interview.guide.modules.interview.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 面试答案实体
 */
@Entity
@Table(name = "interview_answers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_interview_answer_session_question", columnNames = {"session_id", "question_index"})
    },
    indexes = {
        @Index(name = "idx_interview_answer_session_question", columnList = "session_id,question_index")
    })
public class InterviewAnswerEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 关联的会话
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;
    
    // 问题索引
    @Column(name = "question_index")
    private Integer questionIndex;
    
    // 问题内容
    @Column(columnDefinition = "TEXT")
    private String question;
    
    // 问题类别
    private String category;
    
    // 用户答案
    @Column(columnDefinition = "TEXT")
    private String userAnswer;
    
    // 得分 (0-100)
    private Integer score;
    
    // 反馈
    @Column(columnDefinition = "TEXT")
    private String feedback;
    
    // 参考答案
    @Column(columnDefinition = "TEXT")
    private String referenceAnswer;
    
    // 关键点 (JSON)
    @Column(columnDefinition = "TEXT")
    private String keyPointsJson;
    
    // 回答时间
    @Column(nullable = false)
    private LocalDateTime answeredAt;

    // 难度等级 (BASIC, ADVANCED, EXPERT)
    @Column(length = 20)
    private String difficulty = "BASIC";

    // 关联的知识库ID
    private Long knowledgeBaseId;

    // 参考上下文（AI生成问题时的参考内容）
    @Column(columnDefinition = "TEXT")
    private String referenceContext;

    // 问题生成时间
    private LocalDateTime generatedAt;

    // 出题视角ID（该题由哪个视角出）
    @Column(name = "created_by_perspective_id")
    private Long createdByPerspectiveId;

    // 出题视角名称（如"技术面试官"）
    @Column(name = "created_by_perspective_name", length = 100)
    private String createdByPerspectiveName;

    // 仅能看到此答案的视角列表（JSON数组，隐私隔离）
    @Column(name = "visible_to_perspectives", columnDefinition = "TEXT")
    private String visibleToPerspectives;

    // 是否为追问
    @Column(name = "is_follow_up")
    private Boolean isFollowUp;

    // 关联的问题索引（追问时填写）
    @Column(name = "related_index")
    private Integer relatedIndex;

    // 关联的问题摘要（追问时填写）
    @Column(name = "related_question", columnDefinition = "TEXT")
    private String relatedQuestion;

    @PrePersist
    protected void onCreate() {
        answeredAt = LocalDateTime.now();
        generatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public InterviewSessionEntity getSession() {
        return session;
    }
    
    public void setSession(InterviewSessionEntity session) {
        this.session = session;
    }
    
    public Integer getQuestionIndex() {
        return questionIndex;
    }
    
    public void setQuestionIndex(Integer questionIndex) {
        this.questionIndex = questionIndex;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getUserAnswer() {
        return userAnswer;
    }
    
    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
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
    
    public String getReferenceAnswer() {
        return referenceAnswer;
    }
    
    public void setReferenceAnswer(String referenceAnswer) {
        this.referenceAnswer = referenceAnswer;
    }
    
    public String getKeyPointsJson() {
        return keyPointsJson;
    }
    
    public void setKeyPointsJson(String keyPointsJson) {
        this.keyPointsJson = keyPointsJson;
    }
    
    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }
    
    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getReferenceContext() {
        return referenceContext;
    }

    public void setReferenceContext(String referenceContext) {
        this.referenceContext = referenceContext;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Long getCreatedByPerspectiveId() {
        return createdByPerspectiveId;
    }

    public void setCreatedByPerspectiveId(Long createdByPerspectiveId) {
        this.createdByPerspectiveId = createdByPerspectiveId;
    }

    public String getCreatedByPerspectiveName() {
        return createdByPerspectiveName;
    }

    public void setCreatedByPerspectiveName(String createdByPerspectiveName) {
        this.createdByPerspectiveName = createdByPerspectiveName;
    }

    public String getVisibleToPerspectives() {
        return visibleToPerspectives;
    }

    public void setVisibleToPerspectives(String visibleToPerspectives) {
        this.visibleToPerspectives = visibleToPerspectives;
    }

    public Boolean getIsFollowUp() {
        return isFollowUp;
    }

    public void setIsFollowUp(Boolean isFollowUp) {
        this.isFollowUp = isFollowUp;
    }

    public Integer getRelatedIndex() {
        return relatedIndex;
    }

    public void setRelatedIndex(Integer relatedIndex) {
        this.relatedIndex = relatedIndex;
    }

    public String getRelatedQuestion() {
        return relatedQuestion;
    }

    public void setRelatedQuestion(String relatedQuestion) {
        this.relatedQuestion = relatedQuestion;
    }
}

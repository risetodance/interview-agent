package interview.guide.modules.interview.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 面试官角色实体
 * 用于多视角面试中不同面试官角色的配置
 */
@Entity
@Table(name = "interviewer_roles", indexes = {
    @Index(name = "idx_interviewer_role_code", columnList = "role_code"),
    @Index(name = "idx_interviewer_role_status", columnList = "status")
})
public class InterviewerRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 角色名称，如"技术面试官"
    @Column(nullable = false, length = 100)
    private String roleName;

    // 角色编码，如"TECH_INTERVIEWER"
    @Column(nullable = false, unique = true, length = 50)
    private String roleCode;

    // 角色描述
    @Column(columnDefinition = "TEXT")
    private String description;

    // 出题 Prompt（Text）
    @Column(name = "question_prompt", columnDefinition = "TEXT")
    private String questionPrompt;

    // 评分 Prompt（Text）
    @Column(nullable = false, columnDefinition = "TEXT")
    private String scoringPrompt;

    // 权重（0.0-1.0）
    @Column(columnDefinition = "DECIMAL(3,2)")
    private Double weight = 1.0;

    // 图标标识
    @Column(length = 50)
    private String icon;

    // 排序顺序
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    // 是否启用
    @Column(nullable = false)
    private Boolean status = true;

    // 是否为默认模板
    @Column(name = "default_template", nullable = false)
    private Boolean defaultTemplate = false;

    // 创建时间
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 更新时间
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getQuestionPrompt() {
        return questionPrompt;
    }

    public void setQuestionPrompt(String questionPrompt) {
        this.questionPrompt = questionPrompt;
    }

    public String getScoringPrompt() {
        return scoringPrompt;
    }

    public void setScoringPrompt(String scoringPrompt) {
        this.scoringPrompt = scoringPrompt;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public Boolean getDefaultTemplate() {
        return defaultTemplate;
    }

    public void setDefaultTemplate(Boolean defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package interview.guide.modules.admin.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_operator", columnList = "operatorId"),
    @Index(name = "idx_audit_target", columnList = "targetType, targetId"),
    @Index(name = "idx_audit_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 操作类型（如：CREATE, UPDATE, DELETE, LOGIN, LOGOUT 等）
     */
    @Column(nullable = false, length = 50)
    private String operationType;

    /**
     * 操作人ID
     */
    @Column(nullable = false)
    private Long operatorId;

    /**
     * 操作人用户名
     */
    @Column(length = 50)
    private String operatorUsername;

    /**
     * 操作人角色
     */
    @Column(length = 20)
    private String operatorRole;

    /**
     * 目标类型（如：USER, RESUME, INTERVIEW, KNOWLEDGEBASE 等）
     */
    @Column(length = 50)
    private String targetType;

    /**
     * 目标ID
     */
    private Long targetId;

    /**
     * 操作详情（JSON格式）
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * 请求IP地址
     */
    @Column(length = 50)
    private String ipAddress;

    /**
     * 请求方法
     */
    @Column(length = 10)
    private String method;

    /**
     * 请求URL
     */
    @Column(length = 500)
    private String requestUrl;

    /**
     * User-Agent
     */
    @Column(length = 500)
    private String userAgent;

    /**
     * 操作结果（SUCCESS/FAILED）
     */
    @Column(length = 20)
    private String result;

    /**
     * 错误信息
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 操作耗时（毫秒）
     */
    private Long duration;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

package interview.guide.modules.admin.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统配置实体
 */
@Entity
@Table(name = "system_config", indexes = {
    @Index(name = "idx_config_key", columnList = "configKey", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 配置键（唯一）
     */
    @Column(nullable = false, unique = true, length = 100)
    private String configKey;

    /**
     * 配置值
     */
    @Column(columnDefinition = "TEXT")
    private String configValue;

    /**
     * 配置描述
     */
    @Column(length = 500)
    private String description;

    /**
     * 配置类型（如：STRING, INTEGER, BOOLEAN, JSON）
     */
    @Column(length = 20)
    private String configType;

    /**
     * 是否可编辑
     */
    @Builder.Default
    private Boolean editable = true;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
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
}

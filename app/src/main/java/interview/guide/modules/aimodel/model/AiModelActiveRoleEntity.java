package interview.guide.modules.aimodel.model;

import interview.guide.modules.aimodel.enums.AiModelConfigType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 模型角色槽位实体
 * <p>角色指派重构核心表：固定两行（CHAT / SMALL_CHAT），各自指向一个 {@link AiModelConfigEntity} 的 id。
 * <ul>
 *   <li>一条 config 可同时被两个角色引用（主和小都用同一凭证）。</li>
 *   <li>SMALL_CHAT 的 configId 为空 = 禁用，运行时退化使用主模型。</li>
 *   <li>CHAT 的 configId 为空 = 启动 fail-fast（业务不可无主模型）。</li>
 * </ul>
 */
@Entity
@Table(name = "ai_model_active_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiModelActiveRoleEntity {

    /** 角色槽位主键：CHAT / SMALL_CHAT */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private AiModelConfigType role;

    /** 当前指派的配置 id（可空：SMALL_CHAT 空 = 退化主模型；CHAT 不允许空） */
    @Column(name = "config_id")
    private Long configId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

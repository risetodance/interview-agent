package interview.guide.modules.aimodel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 模型配置实体（纯凭证）
 * <p>角色指派重构后：凭证与角色解耦，本表只存连接凭证，不绑任何角色。
 * 角色指派见 {@link AiModelActiveRoleEntity}（ai_model_active_role，CHAT/SMALL_CHAT 两行槽位）。
 * <p>api_key 明文存储（不做加密）；查询接口对外不返回 key，脱敏在 DTO 层完成（DTO 不含 apiKey 字段）
 */
@Entity
@Table(
        name = "ai_model_config",
        indexes = {
                @Index(name = "idx_ai_model_config_created", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiModelConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 供应商标识：minimax / glm / custom */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /** 展示名：如「主对话模型」 */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** OpenAI 兼容 base URL，如 https://api.minimaxi.com/ */
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    /** 明文 API Key（查询接口对外脱敏） */
    @Column(name = "api_key", nullable = false, columnDefinition = "TEXT")
    private String apiKey;

    /** 模型名，如 MiniMax-M2.7 / glm-5.2 */
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /** 默认温度 */
    @Column(name = "temperature")
    @Builder.Default
    private Double temperature = 0.2;

    /** 是否支持视觉（图片输入）：勾选后简历 PDF 解析可调用该模型识图（扫描件/复杂排版兜底），默认不支持 */
    @Column(name = "supports_vision", nullable = false)
    @Builder.Default
    private Boolean supportsVision = false;

    /** 是否视觉优先：true=简历 PDF 一律先视觉识别（失败回退文本）；false=文本解析失败/过少才兜底视觉。
     *  仅 supportsVision=true 时有意义（service 层校验依赖关系）。 */
    @Column(name = "vision_priority", nullable = false)
    @Builder.Default
    private Boolean visionPriority = false;

    /** 最近一次测试连接时间 */
    @Column(name = "last_test_at")
    private LocalDateTime lastTestAt;

    /** 最近一次测试结果 */
    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

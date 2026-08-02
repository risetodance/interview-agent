package interview.guide.modules.aimodel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 模型配置对外 DTO（纯凭证）
 * <p><b>不含 api key 字段</b>：列表 / 详情接口永不返回 key，前端固定展示 ******
 * <p>角色指派重构后：DTO 不再带 configType / isDefault / enabled，
 * 当前被哪些角色引用由列表响应里的 activeRoles 映射给出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelConfigDTO {

    private Long id;
    private String provider;
    private String displayName;
    private String baseUrl;
    private String modelName;
    private Double temperature;
    private LocalDateTime lastTestAt;
    private Boolean lastTestOk;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

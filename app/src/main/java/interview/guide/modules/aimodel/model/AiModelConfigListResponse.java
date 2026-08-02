package interview.guide.modules.aimodel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 模型配置列表响应
 * <p>角色指派重构后：列表同时返回「全部凭证」与「当前角色指派映射」，供前端渲染双卡 + 列表角色 badge。
 *
 * @param configs     全部凭证（按创建时间倒序）
 * @param activeRoles 当前角色指派（CHAT / SMALL_CHAT → configId 或 null）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelConfigListResponse {

    private List<AiModelConfigDTO> configs;
    private ActiveRoles activeRoles;
}

package interview.guide.modules.aimodel.repository;

import interview.guide.modules.aimodel.model.AiModelConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * AI 模型配置 Repository（纯凭证）
 * <p>角色指派重构后：凭证表不再有 config_type / is_default / enabled，
 * 故去掉 findByConfigType / findByConfigTypeAndIsDefaultTrue / clearDefaultForOthers / countByConfigType。
 * 角色指派查询走 {@link AiModelActiveRoleRepository}。
 */
@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfigEntity, Long> {
}

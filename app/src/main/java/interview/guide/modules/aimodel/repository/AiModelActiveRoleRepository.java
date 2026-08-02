package interview.guide.modules.aimodel.repository;

import interview.guide.modules.aimodel.enums.AiModelConfigType;
import interview.guide.modules.aimodel.model.AiModelActiveRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI 模型角色槽位 Repository
 * <p>固定两行（CHAT / SMALL_CHAT），通过 role 主键定位，读 / 写当前指派的 configId。
 */
@Repository
public interface AiModelActiveRoleRepository extends JpaRepository<AiModelActiveRoleEntity, AiModelConfigType> {

    /**
     * 按角色槽位主键查询当前指派记录
     */
    Optional<AiModelActiveRoleEntity> findByRole(AiModelConfigType role);
}

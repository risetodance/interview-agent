package interview.guide.modules.admin.repository;

import interview.guide.modules.admin.model.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置 Repository
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, Long> {

    /**
     * 根据配置键查询配置
     *
     * @param configKey 配置键
     * @return 配置实体
     */
    Optional<SystemConfigEntity> findByConfigKey(String configKey);

    /**
     * 根据配置键查询配置（可编辑）
     *
     * @param configKey 配置键
     * @param editable  是否可编辑
     * @return 配置实体
     */
    Optional<SystemConfigEntity> findByConfigKeyAndEditable(String configKey, Boolean editable);

    /**
     * 检查配置键是否存在
     *
     * @param configKey 配置键
     * @return 是否存在
     */
    boolean existsByConfigKey(String configKey);

    /**
     * 根据配置类型查询配置列表
     *
     * @param configType 配置类型
     * @return 配置列表
     */
    List<SystemConfigEntity> findByConfigType(String configType);

    /**
     * 查询所有可编辑的配置
     *
     * @param editable 是否可编辑
     * @return 配置列表
     */
    List<SystemConfigEntity> findByEditable(Boolean editable);

    /**
     * 根据配置键列表批量查询
     *
     * @param configKeys 配置键列表
     * @return 配置列表
     */
    List<SystemConfigEntity> findByConfigKeyIn(List<String> configKeys);
}

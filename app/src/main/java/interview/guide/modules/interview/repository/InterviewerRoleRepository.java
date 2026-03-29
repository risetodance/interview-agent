package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewerRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 面试官角色Repository
 */
@Repository
public interface InterviewerRoleRepository extends JpaRepository<InterviewerRoleEntity, Long> {

    /**
     * 根据角色编码查找
     */
    Optional<InterviewerRoleEntity> findByRoleCode(String roleCode);

    /**
     * 获取所有启用的角色，按排序顺序
     */
    List<InterviewerRoleEntity> findByStatusTrueOrderBySortOrderAsc();

    /**
     * 获取所有启用的默认模板角色
     */
    List<InterviewerRoleEntity> findByStatusTrueAndDefaultTemplateTrueOrderBySortOrderAsc();

    /**
     * 根据ID列表查找
     */
    List<InterviewerRoleEntity> findAllByIdIn(List<Long> ids);

    /**
     * 根据ID列表查找启用的角色
     */
    @Query("SELECT r FROM InterviewerRoleEntity r WHERE r.id IN :ids AND r.status = true ORDER BY r.sortOrder ASC")
    List<InterviewerRoleEntity> findEnabledRolesByIds(@Param("ids") List<Long> ids);

    /**
     * 检查角色编码是否存在
     */
    boolean existsByRoleCode(String roleCode);

    /**
     * 检查角色编码是否存在（排除指定ID）
     */
    boolean existsByRoleCodeAndIdNot(String roleCode, Long id);
}

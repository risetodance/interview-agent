package interview.guide.modules.admin.repository;

import interview.guide.modules.admin.enums.AdminStatus;
import interview.guide.modules.admin.model.AdminUserEntity;
import interview.guide.modules.admin.enums.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 管理员用户 Repository
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, Long> {

    /**
     * 根据用户名查询管理员
     *
     * @param username 用户名
     * @return 管理员实体
     */
    Optional<AdminUserEntity> findByUsername(String username);

    /**
     * 根据邮箱查询管理员
     *
     * @param email 邮箱
     * @return 管理员实体
     */
    Optional<AdminUserEntity> findByEmail(String email);

    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     *
     * @param email 邮箱
     * @return 是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 根据角色查询管理员列表
     *
     * @param role 角色
     * @return 管理员列表
     */
    List<AdminUserEntity> findByRole(AdminRole role);

    /**
     * 根据状态查询管理员列表
     *
     * @param status 状态
     * @return 管理员列表
     */
    List<AdminUserEntity> findByStatus(AdminStatus status);
}

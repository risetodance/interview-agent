package interview.guide.modules.user.repository;

import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户 Repository
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户实体
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * 根据状态查询用户（分页）
     */
    Page<UserEntity> findByStatus(UserStatus status, Pageable pageable);

    /**
     * 关键词搜索用户
     */
    Page<UserEntity> findByUsernameContainingOrEmailContaining(String username, String email, Pageable pageable);

    /**
     * 统计指定状态的用户数量
     */
    long countByStatus(UserStatus status);

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
}

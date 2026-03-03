package interview.guide.modules.membership.repository;

import interview.guide.modules.membership.enums.PointsType;
import interview.guide.modules.membership.model.PointsRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 积分记录 Repository
 */
@Repository
public interface PointsRecordRepository extends JpaRepository<PointsRecordEntity, Long> {

    /**
     * 按时间倒序查询用户的积分记录
     *
     * @param userId 用户ID
     * @return 积分记录列表（按时间倒序）
     */
    List<PointsRecordEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 查询用户是否已领取过指定业务ID的积分
     *
     * @param userId      用户ID
     * @param type        积分类型
     * @param businessId  业务ID
     * @return 积分记录（如果存在）
     */
    Optional<PointsRecordEntity> findByUserIdAndTypeAndBusinessId(Long userId, PointsType type, Long businessId);

    /**
     * 查询用户是否已完成指定面试
     *
     * @param userId      用户ID
     * @param type        积分类型
     * @param businessId  面试ID
     * @return 积分记录（如果存在）
     */
    boolean existsByUserIdAndTypeAndBusinessId(Long userId, PointsType type, Long businessId);

    /**
     * 查询用户今天是否已签到
     *
     * @param userId  用户ID
     * @param type    积分类型（签到）
     * @param startOfDay 今天开始时间
     * @param endOfDay   今天结束时间
     * @return 签到记录（如果存在）
     */
    @Query("SELECT r FROM PointsRecordEntity r WHERE r.userId = :userId AND r.type = :type AND r.createdAt >= :startOfDay AND r.createdAt < :endOfDay")
    Optional<PointsRecordEntity> findTodaySignIn(@Param("userId") Long userId, @Param("type") PointsType type,
                                                   @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);
}

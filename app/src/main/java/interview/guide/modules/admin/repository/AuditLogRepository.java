package interview.guide.modules.admin.repository;

import interview.guide.modules.admin.model.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志 Repository
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /**
     * 根据操作人ID查询审计日志
     *
     * @param operatorId 操作人ID
     * @return 审计日志列表
     */
    List<AuditLogEntity> findByOperatorId(Long operatorId);

    /**
     * 根据操作类型查询审计日志
     *
     * @param operationType 操作类型
     * @return 审计日志列表
     */
    List<AuditLogEntity> findByOperationType(String operationType);

    /**
     * 根据目标类型和目标ID查询审计日志
     *
     * @param targetType 目标类型
     * @param targetId    目标ID
     * @return 审计日志列表
     */
    List<AuditLogEntity> findByTargetTypeAndTargetId(String targetType, Long targetId);

    /**
     * 根据时间范围查询审计日志
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 审计日志列表
     */
    List<AuditLogEntity> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 分页查询审计日志
     *
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 根据操作人ID分页查询审计日志
     *
     * @param operatorId 操作人ID
     * @param pageable  分页参数
     * @return 分页结果
     */
    Page<AuditLogEntity> findByOperatorIdOrderByCreatedAtDesc(Long operatorId, Pageable pageable);

    /**
     * 根据操作类型分页查询审计日志
     */
    Page<AuditLogEntity> findByOperationType(String operationType, Pageable pageable);

    /**
     * 根据操作人ID分页查询审计日志
     */
    Page<AuditLogEntity> findByOperatorId(Long operatorId, Pageable pageable);

    /**
     * 根据时间范围分页查询审计日志
     */
    Page<AuditLogEntity> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
}

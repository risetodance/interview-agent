package interview.guide.modules.admin.service;

import interview.guide.modules.admin.model.AuditLogEntity;
import interview.guide.modules.admin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * 记录审计日志
     */
    @Async
    public void logOperation(String operationType, Long operatorId, String operatorUsername,
                            String targetType, Long targetId, String details, String ipAddress) {
        AuditLogEntity auditLog = AuditLogEntity.builder()
                .operationType(operationType)
                .operatorId(operatorId)
                .operatorUsername(operatorUsername)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);
        log.debug("记录审计日志: operationType={}, operator={}", operationType, operatorUsername);
    }

    /**
     * 获取审计日志（分页）
     */
    public Page<AuditLogEntity> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    /**
     * 根据操作类型查询审计日志
     */
    public Page<AuditLogEntity> getAuditLogsByOperationType(String operationType, Pageable pageable) {
        return auditLogRepository.findByOperationType(operationType, pageable);
    }

    /**
     * 根据操作人ID查询审计日志
     */
    public Page<AuditLogEntity> getAuditLogsByOperatorId(Long operatorId, Pageable pageable) {
        return auditLogRepository.findByOperatorId(operatorId, pageable);
    }

    /**
     * 根据时间范围查询审计日志
     */
    public Page<AuditLogEntity> getAuditLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findByCreatedAtBetween(startDate, endDate, pageable);
    }

    /**
     * 根据目标类型和ID查询审计日志
     */
    public List<AuditLogEntity> getAuditLogsByTarget(String targetType, Long targetId) {
        return auditLogRepository.findByTargetTypeAndTargetId(targetType, targetId);
    }

    /**
     * 记录用户登录日志
     */
    public void logLogin(Long userId, String username, String ipAddress) {
        logOperation("LOGIN", userId, username, "USER", userId, "用户登录", ipAddress);
    }

    /**
     * 记录用户登出日志
     */
    public void logLogout(Long userId, String username, String ipAddress) {
        logOperation("LOGOUT", userId, username, "USER", userId, "用户登出", ipAddress);
    }

    /**
     * 记录用户操作日志
     */
    public void logUserOperation(String operation, Long userId, String username, String details, String ipAddress) {
        logOperation(operation, userId, username, "USER", userId, details, ipAddress);
    }
}

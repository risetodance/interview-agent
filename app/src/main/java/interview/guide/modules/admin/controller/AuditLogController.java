package interview.guide.modules.admin.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.admin.model.AuditLogEntity;
import interview.guide.modules.admin.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 获取审计日志（分页）
     * GET /api/admin/audit-logs
     */
    @GetMapping
    public Result<Page<AuditLogEntity>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("获取审计日志: page={}, size={}, operationType={}, operatorId={}, startDate={}, endDate={}",
                page, size, operationType, operatorId, startDate, endDate);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogEntity> logs;

        if (startDate != null && endDate != null) {
            logs = auditLogService.getAuditLogsByDateRange(startDate, endDate, pageable);
        } else if (operationType != null && !operationType.isEmpty()) {
            logs = auditLogService.getAuditLogsByOperationType(operationType, pageable);
        } else if (operatorId != null) {
            logs = auditLogService.getAuditLogsByOperatorId(operatorId, pageable);
        } else {
            logs = auditLogService.getAuditLogs(pageable);
        }

        return Result.success(logs);
    }

    /**
     * 根据目标类型和ID查询审计日志
     * GET /api/admin/audit-logs/target
     */
    @GetMapping("/target")
    public Result<List<AuditLogEntity>> getAuditLogsByTarget(
            @RequestParam String targetType,
            @RequestParam Long targetId) {
        log.info("根据目标查询审计日志: targetType={}, targetId={}", targetType, targetId);
        List<AuditLogEntity> logs = auditLogService.getAuditLogsByTarget(targetType, targetId);
        return Result.success(logs);
    }
}

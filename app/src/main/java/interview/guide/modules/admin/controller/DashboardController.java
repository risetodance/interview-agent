package interview.guide.modules.admin.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.admin.model.AuditLogEntity;
import interview.guide.modules.admin.service.AuditLogService;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员仪表盘控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * 获取仪表盘统计数据
     * GET /api/admin/dashboard/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        log.info("获取仪表盘统计数据");

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.count() - userRepository.countByStatus(
            interview.guide.modules.user.model.UserStatus.BANNED);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("pendingUsers", 0L);
        stats.put("totalInterviews", 0L);
        stats.put("totalResumes", 0L);
        stats.put("totalKnowledgeBases", 0L);

        return Result.success(stats);
    }

    /**
     * 获取最近活动
     * GET /api/admin/dashboard/activities
     */
    @GetMapping("/activities")
    public Result<List<AuditLogEntity>> getRecentActivities(@RequestParam(defaultValue = "10") int limit) {
        log.info("获取最近活动: limit={}", limit);

        Pageable pageable = PageRequest.of(0, limit);
        var page = auditLogService.getAuditLogs(pageable);

        return Result.success(page.getContent());
    }
}

package interview.guide.modules.interview.workflow;

import interview.guide.modules.interview.model.InterviewSessionEntity.WorkflowStatus;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流恢复看护（周期扫描）
 * <p>周期扫描 workflow_status = PROCESSING 且「无主或租约已过期」的会话，
 * 从 PostgreSQL checkpoint 断点恢复工作流执行。
 * <p>为什么是周期而不是启动一次：崩溃实例的租约最长 90s 才过期——若实例在租约期内重启，
 * 启动时一次性扫描查不到该会话（租约仍有效），此后再无人扫描，会话将永远卡在 PROCESSING
 * （表现为"重启也恢复不了"）。周期化后，租约一过期（持有者确认死亡）即被本实例接管，
 * 最迟一个租期 + 一个扫描周期（约 2 分钟）内自动恢复。
 * <p>并发安全：多实例同时扫描由恢复前的租约原子抢占互斥；同实例不会重入——
 * 抢到租约的会话 owner=自己且未过期，不满足 recoverable 条件，下轮扫描自然跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRecoveryRunner {

    private final InterviewPersistenceService persistenceService;
    private final WorkflowExecutor workflowExecutor;

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void scanAndRecover() {
        try {
            List<String> sessionIds = persistenceService.findRecoverableSessionIds(WorkflowStatus.PROCESSING);

            if (sessionIds == null || sessionIds.isEmpty()) {
                return;
            }

            log.info("Found {} recoverable workflow(s): {}", sessionIds.size(), sessionIds);

            for (String sessionId : sessionIds) {
                try {
                    log.info("Recovering workflow: sessionId={}", sessionId);
                    workflowExecutor.recoverWorkflow(sessionId);
                } catch (Exception e) {
                    log.error("Failed to recover workflow: sessionId={}, error={}", sessionId, e.getMessage(), e);
                    // 单个会话恢复失败不影响其他会话
                }
            }
        } catch (Exception e) {
            log.error("Workflow recovery scan failed: {}", e.getMessage(), e);
        }
    }
}

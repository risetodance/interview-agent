package interview.guide.modules.interview.workflow;

import interview.guide.modules.interview.model.InterviewSessionEntity.WorkflowStatus;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流启动恢复器
 * 应用启动时扫描 workflow_status = PROCESSING 的会话，
 * 从 PostgreSQL checkpoint 断点恢复工作流执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100) // 确保在其他初始化完成后执行
public class WorkflowRecoveryRunner implements ApplicationRunner {

    private final InterviewPersistenceService persistenceService;
    private final WorkflowExecutor workflowExecutor;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> sessionIds = persistenceService.findSessionIdsByWorkflowStatus(WorkflowStatus.PROCESSING);

            if (sessionIds == null || sessionIds.isEmpty()) {
                log.info("No interrupted workflows to recover");
                return;
            }

            log.info("Found {} interrupted workflow(s) to recover: {}", sessionIds.size(), sessionIds);

            for (String sessionId : sessionIds) {
                try {
                    log.info("Recovering workflow: sessionId={}", sessionId);
                    workflowExecutor.recoverWorkflow(sessionId);
                } catch (Exception e) {
                    log.error("Failed to recover workflow: sessionId={}, error={}", sessionId, e.getMessage(), e);
                    // 单个会话恢复失败不影响其他会话
                }
            }

            log.info("Workflow recovery scan completed");
        } catch (Exception e) {
            log.error("Workflow recovery scan failed: {}", e.getMessage(), e);
        }
    }
}

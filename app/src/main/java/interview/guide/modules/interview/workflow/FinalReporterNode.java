package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.modules.interview.model.InterviewSessionEntity.WorkflowStatus;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import interview.guide.modules.interview.service.InterviewStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 最终报告节点 - 生成面试最终报告
 * 设置 isComplete 标志，评估任务经 outbox 可靠入队（消息队列异步生成评估报告）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalReporterNode {

    private final InterviewPersistenceService persistenceService;
    private final InterviewStreamService interviewStreamService;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value(InterviewWorkflowState.SESSION_ID).orElse(null);

        log.info("Final reporter node: sessionId={}", sessionId);

        if (sessionId == null) {
            log.error("Final reporter node: sessionId is null");
            return state;
        }

        try {
            // 组合事务：更新会话状态 + workflow_status DONE + 评估任务写 outbox
            // （幂等守卫在事务内：仅首次完成时置 PENDING 并入队，重复执行/恢复重放不会重复入队）
            persistenceService.completeInterview(sessionId, WorkflowStatus.DONE);

            // 设置完成标志
            state.updateState(Map.of(
                    InterviewWorkflowState.IS_COMPLETE, true,
                    InterviewWorkflowState.DECISION_ACTION, DecisionAction.FINISH
            ));

            // 直接推送面试完成事件到 SSE
            interviewStreamService.publishComplete(sessionId, Map.of(
                    "sessionId", sessionId,
                    "status", "COMPLETED",
                    "message", "面试已结束，评估报告生成中..."
            ));

            log.info("Final reporter node: interview completed, sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("Final reporter node error: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }

        return state;
    }
}

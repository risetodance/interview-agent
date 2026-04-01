package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.service.InterviewPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 最终报告节点 - 生成面试最终报告
 * 设置 isComplete 标志，触发 SSE 推送面试完成事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalReporterNode {

    private final InterviewPersistenceService persistenceService;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);

        log.info("Final reporter node: sessionId={}", sessionId);

        if (sessionId == null) {
            log.error("Final reporter node: sessionId is null");
            return state;
        }

        try {
            // 获取会话
            Optional<InterviewSessionEntity> sessionOpt = persistenceService.findBySessionId(sessionId);
            if (sessionOpt.isEmpty()) {
                log.error("Final reporter node: session not found, sessionId={}", sessionId);
                return state;
            }
            InterviewSessionEntity session = sessionOpt.get();

            // 更新会话状态为已完成
            if (session.getStatus() != InterviewSessionEntity.SessionStatus.COMPLETED &&
                session.getStatus() != InterviewSessionEntity.SessionStatus.EVALUATED) {
                persistenceService.updateSessionStatus(sessionId, InterviewSessionEntity.SessionStatus.COMPLETED);
            }

            // 设置完成标志
            state.updateState(Map.of(
                    "isComplete", true,
                    "decisionAction", "FINISH"
            ));

            log.info("Final reporter node: interview completed, sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("Final reporter node error: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }

        return state;
    }
}

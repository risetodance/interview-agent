package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 决策节点 - 由 LLM 驱动决策下一步动作
 * 决策类型: ASK (下一题), SWITCH (角色切换), FINISH (结束)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeciderNode {

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        Integer currentIndex = (Integer) state.value("currentQuestionIndex").orElse(0);
        Integer score = (Integer) state.value("score").orElse(0);

        log.info("Decider node: sessionId={}, index={}, score={}", sessionId, currentIndex, score);

        // LLM 决策逻辑
        // 设置 decisionAction: ASK, SWITCH, 或 FINISH

        return state;
    }
}

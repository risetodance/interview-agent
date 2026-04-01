package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 入口节点 - 初始化工作流状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryNode {

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        log.info("Entry node: sessionId={}", sessionId);

        // 初始化问题索引为 0
        if (state.value("currentQuestionIndex").isEmpty()) {
            state.updateState(java.util.Map.of("currentQuestionIndex", 0));
        }

        return state;
    }
}

package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 最终报告节点 - 生成面试最终报告
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalReporterNode {

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);

        log.info("Final reporter node: sessionId={}", sessionId);

        // 报告生成逻辑

        return state;
    }
}

package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 角色切换节点 - 切换视角/角色以出下一题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSwitcherNode {

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        String nextPerspectiveId = (String) state.value("nextPerspectiveId").orElse(null);

        log.info("Role switcher node: sessionId={}, to perspective={}", sessionId, nextPerspectiveId);

        // 角色切换逻辑

        return state;
    }
}

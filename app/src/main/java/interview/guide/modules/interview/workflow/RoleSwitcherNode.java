package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 角色切换节点 - 切换视角/角色以出下一题
 * 注意: 实际的视角切换逻辑在 QuestionGeneratorNode 中轮询选择视角
 * 此节点仅记录切换意图
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSwitcherNode {

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        Integer currentIndex = (Integer) state.value("currentQuestionIndex").orElse(0);

        log.info("Role switcher node: sessionId={}, currentIndex={}", sessionId, currentIndex);

        // 视角切换意图已在 DeciderNode 中设置
        // 实际的视角选择逻辑在 QuestionGeneratorNode 中

        return state;
    }
}

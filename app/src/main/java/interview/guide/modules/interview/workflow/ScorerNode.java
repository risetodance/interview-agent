package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评分节点 - 评估用户答案并更新能力画像
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScorerNode {

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        String currentQuestion = (String) state.value("currentQuestion").orElse(null);
        String currentAnswer = (String) state.value("currentAnswer").orElse(null);

        log.info("Scorer node: sessionId={}, question={}", sessionId, currentQuestion);

        // 评分逻辑将在 SingleAnswerEvaluationService 中实现
        // 更新状态中的 score 和 feedback

        return state;
    }
}

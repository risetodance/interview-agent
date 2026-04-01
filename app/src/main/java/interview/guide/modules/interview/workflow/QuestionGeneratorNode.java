package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 问题生成节点 - 生成面试问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionGeneratorNode {

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        Integer questionIndex = (Integer) state.value("currentQuestionIndex").orElse(0);

        log.info("Question generator node: sessionId={}, index={}", sessionId, questionIndex);

        // 问题生成逻辑将在 QuestionGenerationService 中实现
        // 此节点触发现有的题目生成流程

        return state;
    }
}

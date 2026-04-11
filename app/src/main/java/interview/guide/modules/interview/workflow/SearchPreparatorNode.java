package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 搜索准备器节点 - 准备搜索上下文（关键字、方向匹配审核）
 * 位于 question_generator 之前
 * Decider 和 RoleSwitcher 都指向此节点
 */
@Slf4j
@Component
public class SearchPreparatorNode {

    private final ChatClient chatClient;
    private final StructuredOutputInvoker structuredOutputInvoker;

    public SearchPreparatorNode(ChatClient.Builder chatClientBuilder,
                                StructuredOutputInvoker structuredOutputInvoker) {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
    }

    /**
     * 搜索决策输出 - 包含方向匹配、是否需要搜索、共用关键词、原因
     */
    private record SearchDecisionOutput(
            boolean directionMatch,
            String matchReason,
            boolean needSearch,
            String keywords,
            String reason
    ) {
    }

    private final BeanOutputConverter<SearchDecisionOutput> outputConverter =
            new BeanOutputConverter<>(SearchDecisionOutput.class);

    @Value("classpath:prompts/search-preparator-system.st")
    private Resource systemPromptResource;

    @Value("classpath:prompts/search-preparator-user.st")
    private Resource userPromptResource;

    @Value("${app.mcp.websearch.enabled:true}")
    private boolean mcpSearchEnabled;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value(InterviewWorkflowState.SESSION_ID).orElse(null);
        String currentQuestion = (String) state.value(InterviewWorkflowState.CURRENT_QUESTION).orElse("");
        String userAnswer = (String) state.value(InterviewWorkflowState.CURRENT_ANSWER).orElse("");
        String feedback = (String) state.value(InterviewWorkflowState.FEEDBACK).orElse("");
        String category = (String) state.value(InterviewWorkflowState.CURRENT_CATEGORY).orElse("");
        String currentPerspective = (String) state.value(InterviewWorkflowState.CURRENT_PERSPECTIVE_NAME).orElse("");
        // 获取 DeciderNode 输出的出题方向
        String questionDirection = (String) state.value(InterviewWorkflowState.QUESTION_DIRECTION).orElse("");

        log.info("Search preparator node: sessionId={}, feedback={}, category={}, mcpEnabled={}, questionDirection={}",
                sessionId, feedback, category, mcpSearchEnabled, questionDirection);

        if (!mcpSearchEnabled) {
            log.info("MCP search is disabled, skip search decision");
            state.updateState(Map.of(
                    InterviewWorkflowState.SEARCH_ENABLED, false,
                    InterviewWorkflowState.SEARCH_KEYWORDS, "",
                    InterviewWorkflowState.SEARCH_DECISION_REASON, "MCP搜索未启用"
            ));
            return state;
        }

        log.info("userAnswer is :{}, currentQuestion is {}", userAnswer, currentQuestion);

        try {
            String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String userPromptTemplate = userPromptResource.getContentAsString(StandardCharsets.UTF_8);

            Map<String, Object> variables = new HashMap<>();
            variables.put("currentPerspective", currentPerspective);
            variables.put("currentQuestion", currentQuestion);
            variables.put("userAnswer", userAnswer);
            variables.put("feedback", feedback);
            variables.put("category", category);
            variables.put("questionDirection", questionDirection);

            String userPrompt = userPromptTemplate;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                userPrompt = userPrompt.replace("{{" + entry.getKey() + "}}",
                        entry.getValue() != null ? entry.getValue().toString() : "");
            }

            SearchDecisionOutput decision = structuredOutputInvoker.invoke(
                    chatClient, systemPrompt, userPrompt, outputConverter,
                    ErrorCode.AI_SERVICE_ERROR, "搜索准备解析失败", "SearchPreparatorNode", log
            );

            log.info("Search decider: directionMatch={}, matchReason={}, needSearch={}, keywords={}, reason={}",
                    decision.directionMatch(), decision.matchReason(), decision.needSearch(), decision.keywords(), decision.reason());

            state.updateState(Map.of(
                    InterviewWorkflowState.DIRECTION_MATCH, decision.directionMatch(),
                    InterviewWorkflowState.SEARCH_ENABLED, decision.needSearch(),
                    InterviewWorkflowState.SEARCH_KEYWORDS, decision.keywords() != null ? decision.keywords() : "",
                    InterviewWorkflowState.SEARCH_DECISION_REASON, decision.reason()
            ));

        } catch (Exception e) {
            log.error("Search decider error: {}", e.getMessage(), e);
            state.updateState(Map.of(
                    InterviewWorkflowState.SEARCH_ENABLED, false,
                    InterviewWorkflowState.SEARCH_KEYWORDS, "",
                    InterviewWorkflowState.SEARCH_DECISION_REASON, "决策失败: " + e.getMessage()
            ));
        }

        return state;
    }
}

package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Web搜索节点 - 调用 MCP web_search
 * search_decider 决定需要搜索时执行此节点
 */
@Slf4j
@Component
public class WebSearchNode {

    private final ToolCallbackProvider toolCallbackProvider;
    private final ChatClient.Builder chatClientBuilder;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;

    public WebSearchNode(
            @Autowired(required = false) ToolCallbackProvider toolCallbackProvider,
            @Autowired(required = false) ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            ObjectMapper objectMapper) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatClientBuilder = chatClientBuilder;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.objectMapper = objectMapper;
    }

    /**
     * Web搜索结果输出 - 包含题目、参考答案、知识点
     */
    public record WebSearchOutput(
            String question,
            String referenceAnswer,
            List<String> keyPoints
    ) {
        /**
         * 序列化为JSON字符串
         */
        public String serialize(ObjectMapper objectMapper) {
            try {
                return objectMapper.writeValueAsString(this);
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    private final BeanOutputConverter<WebSearchOutput> outputConverter =
            new BeanOutputConverter<>(WebSearchOutput.class);

    @Value("classpath:prompts/web-search-system.st")
    private Resource systemPromptResource;

    @Value("classpath:prompts/web-search-user.st")
    private Resource userPromptResource;

    public OverAllState execute(OverAllState state) {
        String sessionId = (String) state.value("sessionId").orElse(null);
        String keywords = (String) state.value("searchKeywords").orElse("");
        Boolean searchEnabled = (Boolean) state.value("searchEnabled").orElse(false);

        log.info("Web search node: sessionId={}, enabled={}, keywords={}", sessionId, searchEnabled, keywords);

        if (!searchEnabled || keywords.isBlank()) {
            log.info("Web search skipped: enabled={}, keywords={}", searchEnabled, keywords);
            state.updateState(Map.of("searchResult", "", "searchEnabled", false));
            return state;
        }

        if (toolCallbackProvider == null || chatClientBuilder == null) {
            log.warn("ToolCallbackProvider or ChatClient is null, skip search");
            state.updateState(Map.of("searchResult", "", "searchEnabled", false));
            return state;
        }

        try {
            // 通过 ToolCallbackProvider 获取 web_search 工具
            ToolCallback webSearchCallback = null;
            for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
                if ("web_search".equals(callback.getToolDefinition().name())) {
                    webSearchCallback = callback;
                    break;
                }
            }

            if (webSearchCallback == null) {
                log.warn("web_search tool not found in ToolCallbackProvider");
                state.updateState(Map.of("searchResult", "", "searchEnabled", false));
                return state;
            }

            // 使用 StructuredOutputInvoker 调用，包含 toolCallback
            String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String userPrompt = userPromptResource.getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{keywords}}", keywords);

            WebSearchOutput output = structuredOutputInvoker.invoke(
                    chatClientBuilder.build(),
                    systemPrompt,
                    userPrompt,
                    outputConverter,
                    ErrorCode.AI_SERVICE_ERROR,
                    "搜索结果处理失败",
                    "WebSearchNode",
                    log,
                    webSearchCallback  // 传递 toolCallback
            );

            String searchResult = output != null ? output.serialize(objectMapper) : "{}";

            log.info("Web search completed: sessionId={}, resultLength={}", sessionId, searchResult.length());

            state.updateState(Map.of(
                    "searchResult", searchResult,
                    "searchEnabled", !searchResult.isBlank()
            ));

        } catch (Exception e) {
            log.error("Web search error: {}", e.getMessage(), e);
            state.updateState(Map.of("searchResult", "", "searchEnabled", false));
        }

        return state;
    }
}

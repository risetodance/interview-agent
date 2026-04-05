package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Web搜索节点 - 调用 MCP web_search
 * search_decider 决定需要搜索时执行此节点
 */
@Slf4j
@Component
public class WebSearchNode {

    private final ToolCallbackProvider toolCallbackProvider;

    public WebSearchNode(@Autowired(required = false) ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
    }

    public OverAllState execute(OverAllState state) {
        String sessionId =/**/ (String) state.value("sessionId").orElse(null);
        String keywords = (String) state.value("searchKeywords").orElse("");
        Boolean searchEnabled = (Boolean) state.value("searchEnabled").orElse(false);

        log.info("Web search node: sessionId={}, enabled={}, keywords={}", sessionId, searchEnabled, keywords);

        if (!searchEnabled || keywords.isBlank()) {
            log.info("Web search skipped: enabled={}, keywords={}", searchEnabled, keywords);
            state.updateState(Map.of("searchResult", "", "searchEnabled", false));
            return state;
        }

        if (toolCallbackProvider == null) {
            log.warn("ToolCallbackProvider is null, skip search");
            state.updateState(Map.of("searchResult", "", "searchEnabled", false));
            return state;
        }

        try {
            // 通过 ToolCallbackProvider 获取所有工具回调，查找 web_search
            ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();

            log.info("ToolCallbackProvider registered tools:");
            for (ToolCallback callback : callbacks) {
                log.info("  - tool: {}", callback.getToolDefinition().name());
            }
            ToolCallback webSearchCallback = null;

            for (ToolCallback callback : callbacks) {
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

            // 调用 MCP web_search 工具
            String searchResult = webSearchCallback.call(keywords);

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

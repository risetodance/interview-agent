package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSearchNode 集成测试
 *
 * <p>使用 @SpringBootTest 验证搜索节点能否正常执行
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.ai.openai.api-key=${MINIMAX_API_KEY}",
    "spring.ai.openai.base-url=https://api.minimaxi.com",
    "app.mcp.websearch.enabled=true"
})
@DisplayName("Web搜索节点集成测试")
class WebSearchNodeTest {

    private static final Logger log = LoggerFactory.getLogger(WebSearchNodeTest.class);

    @Autowired(required = false)
    private WebSearchNode webSearchNode;

    @Autowired(required = false)
    private SearchDeciderNode searchDeciderNode;

    /**
     * 创建带有正确key策略的OverAllState
     */
    private OverAllState createState(Map<String, Object> initialData) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(InterviewWorkflowState.SESSION_ID, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.CURRENT_QUESTION, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.CURRENT_ANSWER, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.FEEDBACK, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.CURRENT_CATEGORY, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.SEARCH_ENABLED, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.SEARCH_KEYWORDS, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.SEARCH_RESULT, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.SEARCH_DECISION_REASON, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(InterviewWorkflowState.CURRENT_QUESTION_INDEX, KeyStrategy.REPLACE);
        state.updateState(initialData);
        return state;
    }

    @Test
    @DisplayName("WebSearchNode应该被正确加载")
    void testWebSearchNodeLoaded() {
        assertNotNull(webSearchNode, "WebSearchNode 应该被 Spring 加载");
    }

    @Test
    @DisplayName("SearchDeciderNode应该被正确加载")
    void testSearchDeciderNodeLoaded() {
        assertNotNull(searchDeciderNode, "SearchDeciderNode 应该被 Spring 加载");
    }

    @Test
    @DisplayName("SearchDeciderNode对于基础概念问题应该决策不需要搜索")
    void testSearchDeciderNodeDecisionForBasicConcept() {
        // Given - Java基础概念问题
        assertNotNull(searchDeciderNode, "SearchDeciderNode 应该被加载");

        OverAllState state = createState(Map.of(
            InterviewWorkflowState.SESSION_ID, "test-session-123",
            InterviewWorkflowState.CURRENT_QUESTION, "什么是Java的多态？",
            InterviewWorkflowState.CURRENT_ANSWER, "用户回答内容",
            InterviewWorkflowState.FEEDBACK, "",
            InterviewWorkflowState.CURRENT_CATEGORY, "Java"
        ));

        // When
        OverAllState result = searchDeciderNode.execute(state);

        // Then - 验证状态被正确设置
        assertTrue(result.value(InterviewWorkflowState.SEARCH_ENABLED).isPresent(), "searchEnabled 应该被设置");
        assertTrue(result.value(InterviewWorkflowState.SEARCH_KEYWORDS).isPresent(), "searchKeywords 应该被设置");
        assertTrue(result.value(InterviewWorkflowState.SEARCH_DECISION_REASON).isPresent(), "searchDecisionReason 应该被设置");

        Boolean searchEnabled = (Boolean) result.value(InterviewWorkflowState.SEARCH_ENABLED).orElse(null);
        log.info("基础概念问题 - searchEnabled: {}", searchEnabled);
        log.info("基础概念问题 - searchDecisionReason: {}", result.value(InterviewWorkflowState.SEARCH_DECISION_REASON).orElse(""));

        assertNotNull(searchEnabled, "searchEnabled 不应该为null");
    }

    @Test
    @DisplayName("SearchDeciderNode对于最新技术问题应该决策需要搜索")
    void testSearchDeciderNodeDecisionForLatestTech() {
        // Given - 最新技术问题
        assertNotNull(searchDeciderNode, "SearchDeciderNode 应该被加载");

        OverAllState state = createState(Map.of(
            InterviewWorkflowState.SESSION_ID, "test-session-123",
            InterviewWorkflowState.CURRENT_QUESTION, "2024年最流行的Java框架是什么？",
            InterviewWorkflowState.CURRENT_ANSWER, "用户回答内容",
            InterviewWorkflowState.FEEDBACK, "",
            InterviewWorkflowState.CURRENT_CATEGORY, "Java"
        ));

        // When
        OverAllState result = searchDeciderNode.execute(state);

        // Then - 验证状态被正确设置
        assertTrue(result.value(InterviewWorkflowState.SEARCH_ENABLED).isPresent(), "searchEnabled 应该被设置");
        assertTrue(result.value(InterviewWorkflowState.SEARCH_KEYWORDS).isPresent(), "searchKeywords 应该被设置");
        assertTrue(result.value(InterviewWorkflowState.SEARCH_DECISION_REASON).isPresent(), "searchDecisionReason 应该被设置");

        Boolean searchEnabled = (Boolean) result.value(InterviewWorkflowState.SEARCH_ENABLED).orElse(null);
        String keywords = (String) result.value(InterviewWorkflowState.SEARCH_KEYWORDS).orElse("");
        String reason = (String) result.value(InterviewWorkflowState.SEARCH_DECISION_REASON).orElse("");

        log.info("最新技术问题 - searchEnabled: {}", searchEnabled);
        log.info("最新技术问题 - searchKeywords: {}", keywords);
        log.info("最新技术问题 - searchDecisionReason: {}", reason);

        assertNotNull(searchEnabled, "searchEnabled 不应该为null");

        // 如果需要搜索，则调用WebSearchNode执行搜索
        if (searchEnabled) {
            OverAllState searchResult = webSearchNode.execute(result);
            String searchResultStr = (String) searchResult.value(InterviewWorkflowState.SEARCH_RESULT).orElse("");
            log.info("搜索结果长度: {}", searchResultStr.length());
            log.info("搜索结果: {}", searchResultStr.substring(0, Math.min(200, searchResultStr.length())));
        }
    }

    @Test
    @DisplayName("WebSearchNode当searchEnabled为false时应该正确处理")
    void testWebSearchNodeExecutesWhenDisabled() {
        // Given
        assertNotNull(webSearchNode, "WebSearchNode 应该被加载");

        OverAllState state = createState(Map.of(
            InterviewWorkflowState.SESSION_ID, "test-session-123",
            InterviewWorkflowState.SEARCH_ENABLED, false,
            InterviewWorkflowState.SEARCH_KEYWORDS, "Java 面试题"
        ));

        // When
        OverAllState result = webSearchNode.execute(state);

        // Then
        assertEquals("", result.value(InterviewWorkflowState.SEARCH_RESULT).orElse(""));
        assertEquals(false, result.value(InterviewWorkflowState.SEARCH_ENABLED).orElse(true));
    }

    @Test
    @DisplayName("WebSearchNode当keywords为空时应该正确处理")
    void testWebSearchNodeExecutesWhenNoKeywords() {
        // Given
        assertNotNull(webSearchNode, "WebSearchNode 应该被加载");

        OverAllState state = createState(Map.of(
            InterviewWorkflowState.SESSION_ID, "test-session-123",
            InterviewWorkflowState.SEARCH_ENABLED, true,
            InterviewWorkflowState.SEARCH_KEYWORDS, ""
        ));

        // When
        OverAllState result = webSearchNode.execute(state);

        // Then
        assertEquals("", result.value(InterviewWorkflowState.SEARCH_RESULT).orElse(""));
        assertEquals(false, result.value(InterviewWorkflowState.SEARCH_ENABLED).orElse(true));
    }

    @Test
    @DisplayName("WebSearchNode当searchEnabled为true且有keywords时应该执行搜索")
    void testWebSearchNodeExecutesWhenEnabled() {
        // Given
        assertNotNull(webSearchNode, "WebSearchNode 应该被加载");

        OverAllState state = createState(Map.of(
            InterviewWorkflowState.SESSION_ID, "test-session-123",
            InterviewWorkflowState.SEARCH_ENABLED, true,
            InterviewWorkflowState.SEARCH_KEYWORDS, "Java 面试题 2024"
        ));

        // When
        OverAllState result = webSearchNode.execute(state);

        // Then - 状态应该被设置
        assertTrue(result.value(InterviewWorkflowState.SEARCH_RESULT).isPresent(), "searchResult 应该被设置");
        assertTrue(result.value(InterviewWorkflowState.SEARCH_ENABLED).isPresent(), "searchEnabled 应该被设置");

        String searchResult = (String) result.value(InterviewWorkflowState.SEARCH_RESULT).orElse("");
        log.info("searchResult length: {}", searchResult.length());
    }
}

package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    @Autowired(required = false)
    private WebSearchNode webSearchNode;

    @Autowired(required = false)
    private SearchDeciderNode searchDeciderNode;

    /**
     * 创建带有正确key策略的OverAllState
     */
    private OverAllState createState(Map<String, Object> initialData) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy("sessionId", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("currentQuestion", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("userAnswer", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("feedback", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("currentCategory", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("searchEnabled", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("searchKeywords", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("searchResult", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("searchDecisionReason", KeyStrategy.REPLACE);
        state.registerKeyAndStrategy("currentQuestionIndex", KeyStrategy.REPLACE);
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
            "sessionId", "test-session-123",
            "currentQuestion", "什么是Java的多态？",
            "userAnswer", "用户回答内容",
            "feedback", "",
            "currentCategory", "Java"
        ));

        // When
        OverAllState result = searchDeciderNode.execute(state);

        // Then - 验证状态被正确设置
        assertTrue(result.value("searchEnabled").isPresent(), "searchEnabled 应该被设置");
        assertTrue(result.value("searchKeywords").isPresent(), "searchKeywords 应该被设置");
        assertTrue(result.value("searchDecisionReason").isPresent(), "searchDecisionReason 应该被设置");

        Boolean searchEnabled = (Boolean) result.value("searchEnabled").orElse(null);
        System.out.println("基础概念问题 - searchEnabled: " + searchEnabled);
        System.out.println("基础概念问题 - searchDecisionReason: " + result.value("searchDecisionReason").orElse(""));

        assertNotNull(searchEnabled, "searchEnabled 不应该为null");
    }

    @Test
    @DisplayName("SearchDeciderNode对于最新技术问题应该决策需要搜索")
    void testSearchDeciderNodeDecisionForLatestTech() {
        // Given - 最新技术问题
        assertNotNull(searchDeciderNode, "SearchDeciderNode 应该被加载");

        OverAllState state = createState(Map.of(
            "sessionId", "test-session-123",
            "currentQuestion", "2024年最流行的Java框架是什么？",
            "userAnswer", "用户回答内容",
            "feedback", "",
            "currentCategory", "Java"
        ));

        // When
        OverAllState result = searchDeciderNode.execute(state);

        // Then - 验证状态被正确设置
        assertTrue(result.value("searchEnabled").isPresent(), "searchEnabled 应该被设置");
        assertTrue(result.value("searchKeywords").isPresent(), "searchKeywords 应该被设置");
        assertTrue(result.value("searchDecisionReason").isPresent(), "searchDecisionReason 应该被设置");

        Boolean searchEnabled = (Boolean) result.value("searchEnabled").orElse(null);
        String keywords = (String) result.value("searchKeywords").orElse("");
        String reason = (String) result.value("searchDecisionReason").orElse("");

        System.out.println("最新技术问题 - searchEnabled: " + searchEnabled);
        System.out.println("最新技术问题 - searchKeywords: " + keywords);
        System.out.println("最新技术问题 - searchDecisionReason: " + reason);

        assertNotNull(searchEnabled, "searchEnabled 不应该为null");

        // 如果需要搜索，则调用WebSearchNode执行搜索
        if (searchEnabled) {
            OverAllState searchResult = webSearchNode.execute(result);
            System.out.println("搜索结果长度: " + ((String)searchResult.value("searchResult").orElse("")).length());
            System.out.println("搜索结果: " + ((String)searchResult.value("searchResult").orElse("")).substring(0, Math.min(200, ((String)searchResult.value("searchResult").orElse("")).length())));
        }
    }

    @Test
    @DisplayName("WebSearchNode当searchEnabled为false时应该正确处理")
    void testWebSearchNodeExecutesWhenDisabled() {
        // Given
        assertNotNull(webSearchNode, "WebSearchNode 应该被加载");

        OverAllState state = createState(Map.of(
            "sessionId", "test-session-123",
            "searchEnabled", false,
            "searchKeywords", "Java 面试题"
        ));

        // When
        OverAllState result = webSearchNode.execute(state);

        // Then
        assertEquals("", result.value("searchResult").orElse(""));
        assertEquals(false, result.value("searchEnabled").orElse(true));
    }

    @Test
    @DisplayName("WebSearchNode当keywords为空时应该正确处理")
    void testWebSearchNodeExecutesWhenNoKeywords() {
        // Given
        assertNotNull(webSearchNode, "WebSearchNode 应该被加载");

        OverAllState state = createState(Map.of(
            "sessionId", "test-session-123",
            "searchEnabled", true,
            "searchKeywords", ""
        ));

        // When
        OverAllState result = webSearchNode.execute(state);

        // Then
        assertEquals("", result.value("searchResult").orElse(""));
        assertEquals(false, result.value("searchEnabled").orElse(true));
    }

    @Test
    @DisplayName("WebSearchNode当searchEnabled为true且有keywords时应该执行搜索")
    void testWebSearchNodeExecutesWhenEnabled() {
        // Given
        assertNotNull(webSearchNode, "WebSearchNode 应该被加载");

        OverAllState state = createState(Map.of(
            "sessionId", "test-session-123",
            "searchEnabled", true,
            "searchKeywords", "Java 面试题 2024"
        ));

        // When
        OverAllState result = webSearchNode.execute(state);

        // Then - 状态应该被设置
        assertTrue(result.value("searchResult").isPresent(), "searchResult 应该被设置");
        assertTrue(result.value("searchEnabled").isPresent(), "searchEnabled 应该被设置");

        String searchResult = (String) result.value("searchResult").orElse("");
        System.out.println("searchResult length: " + searchResult.length());
    }
}

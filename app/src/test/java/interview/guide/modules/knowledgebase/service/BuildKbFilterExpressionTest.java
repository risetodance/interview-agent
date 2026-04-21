package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.util.KnowledgeBaseSearchUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * buildKbFilterExpression 方法测试
 * 验证 Spring AI VectorStore 过滤表达式格式正确
 */
@DisplayName("buildKbFilterExpression 测试")
class BuildKbFilterExpressionTest {

    @Test
    @DisplayName("单个知识库ID")
    void testSingleKbId() {
        String result = KnowledgeBaseSearchUtils.buildKbFilterExpression(List.of(1L));
        assertEquals("kb_id IN ['1']", result);
    }

    @Test
    @DisplayName("多个知识库ID")
    void testMultipleKbIds() {
        String result = KnowledgeBaseSearchUtils.buildKbFilterExpression(List.of(1L, 2L, 3L));
        assertEquals("kb_id IN ['1', '2', '3']", result);
    }

    @Test
    @DisplayName("空列表")
    void testEmptyList() {
        String result = KnowledgeBaseSearchUtils.buildKbFilterExpression(List.of());
        assertEquals("", result);
    }

    @Test
    @DisplayName("null列表")
    void testNullList() {
        String result = KnowledgeBaseSearchUtils.buildKbFilterExpression(null);
        assertEquals("", result);
    }

    @Test
    @DisplayName("格式验证 - 必须使用方括号")
    void testFormatWithSquareBrackets() {
        String result = KnowledgeBaseSearchUtils.buildKbFilterExpression(List.of(100L, 200L));

        // 验证使用方括号而非圆括号
        assertTrue(result.contains("["), "应使用方括号");
        assertTrue(result.contains("]"), "应使用方括号");
        assertFalse(result.contains("("), "不应使用圆括号");
        assertFalse(result.contains(")"), "不应使用圆括号");

        assertEquals("kb_id IN ['100', '200']", result);
    }
}
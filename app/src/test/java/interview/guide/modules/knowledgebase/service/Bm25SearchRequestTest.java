package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.util.KnowledgeBaseSearchUtils;
import interview.guide.modules.knowledgebase.util.KnowledgeBaseSearchUtils.Bm25SearchRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bm25SearchRequest 单元测试
 * 测试 BM25 查询 SQL 构建和参数绑定
 */
@DisplayName("BM25搜索请求测试")
class Bm25SearchRequestTest {

    private Bm25SearchRequest buildRequest(String query, int topK, List<Long> kbIds) {
        Bm25SearchRequest request = Bm25SearchRequest.builder()
                .query(query)
                .topK(topK);
        if (kbIds != null) {
            request.knowledgeBaseIds(kbIds);
        }
        return request.build();
    }

    @Nested
    @DisplayName("基本查询构建")
    class BasicQueryTests {

        @Test
        @DisplayName("简单查询 - 无过滤条件")
        void testSimpleQuery() {
            Bm25SearchRequest request = buildRequest("Java编程", 10, null);

            String sql = request.getSql();
            List<Object> params = request.getParams();

            assertTrue(sql.contains("SELECT id, content, metadata, paradedb.score(id) AS rank"));
            assertTrue(sql.contains("FROM vector_store"));
            assertTrue(sql.contains("WHERE content @@@ ?"));
            assertTrue(sql.contains("ORDER BY rank DESC LIMIT ?"));

            assertEquals(2, params.size());
            assertEquals("Java编程", params.get(0));
            assertEquals(10, params.get(1));
        }

        @Test
        @DisplayName("带特殊字符的查询")
        void testQueryWithSpecialChars() {
            Bm25SearchRequest request = buildRequest("Java|Python|Go", 5, null);

            List<Object> params = request.getParams();

            // | 应该被替换为空格
            assertEquals("Java Python Go", params.get(0));
        }

        @Test
        @DisplayName("空查询处理")
        void testEmptyQuery() {
            Bm25SearchRequest request = buildRequest("", 10, null);

            List<Object> params = request.getParams();

            assertEquals("", params.get(0));
        }

        @Test
        @DisplayName("null查询处理")
        void testNullQuery() {
            Bm25SearchRequest request = buildRequest(null, 10, null);

            List<Object> params = request.getParams();

            assertEquals("", params.get(0));
        }
    }

    @Nested
    @DisplayName("知识库ID过滤")
    class KbIdFilterTests {

        @Test
        @DisplayName("单个知识库ID过滤")
        void testSingleKbId() {
            Bm25SearchRequest request = buildRequest("Java", 10, List.of(1L));

            String sql = request.getSql();
            List<Object> params = request.getParams();

            // 验证完整JSONPath格式
            String expectedJsonPath = "($.kb_id == \"1\")";
            assertTrue(sql.contains(expectedJsonPath),
                    "单个ID时应为: " + expectedJsonPath + "，实际: " + sql);
            assertFalse(sql.contains(" || "), "单个ID不应包含 || 分隔符");

            assertEquals(2, params.size());
            assertEquals("Java", params.get(0));
            assertEquals(10, params.get(1));
        }

        @Test
        @DisplayName("多个知识库ID过滤")
        void testMultipleKbIds() {
            Bm25SearchRequest request = buildRequest("Spring", 10, List.of(1L, 2L, 3L));

            String sql = request.getSql();
            List<Object> params = request.getParams();

            // 验证完整JSONPath格式，确保每个条件都有 $.kb_id 前缀
            String expectedJsonPath = "($.kb_id == \"1\" || $.kb_id == \"2\" || $.kb_id == \"3\")";
            assertTrue(sql.contains(expectedJsonPath),
                    "多个ID时应为: " + expectedJsonPath + "，实际: " + sql);

            assertEquals(2, params.size());
            assertEquals("Spring", params.get(0));
            assertEquals(10, params.get(1));
        }

        @Test
        @DisplayName("两个知识库ID过滤")
        void testTwoKbIds() {
            Bm25SearchRequest request = buildRequest("Java", 10, List.of(100L, 200L));

            String sql = request.getSql();

            // 验证完整格式
            String expectedJsonPath = "($.kb_id == \"100\" || $.kb_id == \"200\")";
            assertTrue(sql.contains(expectedJsonPath),
                    "两个ID时应为: " + expectedJsonPath + "，实际: " + sql);
        }
    }

    @Nested
    @DisplayName("SQL语法验证")
    class SqlSyntaxTests {

        @Test
        @DisplayName("完整SQL语句结构")
        void testCompleteSqlStructure() {
            Bm25SearchRequest request = buildRequest("微服务架构", 20, List.of(100L, 200L));

            String sql = request.getSql();

            // 验证完整SQL结构
            assertTrue(sql.startsWith("SELECT id, content, metadata, paradedb.score(id) AS rank"));
            assertTrue(sql.contains("FROM vector_store"));
            assertTrue(sql.contains("WHERE content @@@ ?"));
            assertTrue(sql.contains("metadata::jsonb @@"));
            assertTrue(sql.contains("ORDER BY rank DESC LIMIT ?"));
        }

        @Test
        @DisplayName("TOPK参数正确传递")
        void testTopKParam() {
            Bm25SearchRequest request = buildRequest("测试", 50, null);

            List<Object> params = request.getParams();

            assertEquals(50, params.get(1));
        }

        @Test
        @DisplayName("JSONPath格式正确性")
        void testJsonPathFormat() {
            // 验证JSONPath格式：每个条件都必须有 $. 前缀
            Bm25SearchRequest request = buildRequest("test", 10, List.of(1L, 2L));
            String sql = request.getSql();

            // 验证完整格式：($.kb_id == "1" || $.kb_id == "2")
            String expectedJsonPath = "($.kb_id == \"1\" || $.kb_id == \"2\")";
            assertTrue(sql.contains(expectedJsonPath),
                    "JSONPath格式应为: " + expectedJsonPath + "，实际: " + sql);

            // 验证各个部分都正确
            assertTrue(sql.contains("$.kb_id == \"1\""), "应包含 $.kb_id == \"1\"");
            assertTrue(sql.contains("$.kb_id == \"2\""), "应包含 $.kb_id == \"2\"");
            assertTrue(sql.contains(" || "), "条件之间应有 || 分隔");
        }
    }
}
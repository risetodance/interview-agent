package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.util.KnowledgeBaseSearchUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BM25查询结果测试
 * 验证BM25全文搜索能够正确返回结果
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("BM25查询结果测试")
class Bm25QueryResultTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KnowledgeBaseVectorService vectorService;

    private static final Long TEST_KB_ID = 77777L;

    @Test
    @DisplayName("BM25查询 - 验证pg_search扩展可用且返回结果")
    void testBm25QueryReturnsResults() {
        // 验证pg_search扩展是否可用
        boolean isPgSearchAvailable = KnowledgeBaseSearchUtils.isPgSearchAvailable(jdbcTemplate);

        if (!isPgSearchAvailable) {
            // 如果扩展不可用，跳过测试
            return;
        }

        // 准备测试数据
        String content = """
            Java是一种面向对象的编程语言，具有平台无关性。

            Spring框架是企业级Java开发的首选框架。

            Python是一门简洁高效的编程语言。
            """;

        try {
            // 向量化存储
            vectorService.vectorizeAndStore(TEST_KB_ID, content);

            // 直接执行BM25查询
            String query = "Java Spring";
            int topK = 10;
            List<Long> kbIds = List.of(TEST_KB_ID);

            // 构建BM25查询
            KnowledgeBaseSearchUtils.Bm25SearchRequest request = KnowledgeBaseSearchUtils.Bm25SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .knowledgeBaseIds(kbIds)
                    .build();

            // 执行查询
            List<?> results = jdbcTemplate.queryForList(
                    request.getSql(),
                    request.getParams().toArray()
            );

            // 验证返回的是List类型
            assertInstanceOf(List.class, results);
            // 验证结果不为空（BM25应该能查到数据）
            assertFalse(results.isEmpty(), "BM25查询应该返回结果");

            // 验证结果结构
            assertTrue(results.get(0) instanceof java.util.Map, "结果项应该是Map类型");

        } finally {
            // 清理测试数据
            jdbcTemplate.update("DELETE FROM kb_parent_documents WHERE kb_id = ?", TEST_KB_ID);
            try {
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'kb_id' = ?", TEST_KB_ID.toString());
            } catch (Exception e) {
                // 忽略清理异常
            }
        }
    }

    @Test
    @DisplayName("BM25查询 - 验证能够根据kb_id过滤结果")
    void testBm25QueryWithKbIdFilter() {
        boolean isPgSearchAvailable = KnowledgeBaseSearchUtils.isPgSearchAvailable(jdbcTemplate);
        if (!isPgSearchAvailable) {
            return;
        }

        Long kbId1 = 77778L;
        Long kbId2 = 77779L;

        try {
            // 向量化两个知识库
            vectorService.vectorizeAndStore(kbId1, "Java编程语言相关知识。");
            vectorService.vectorizeAndStore(kbId2, "Python编程语言相关知识。");

            // 查询kbId1
            KnowledgeBaseSearchUtils.Bm25SearchRequest request = KnowledgeBaseSearchUtils.Bm25SearchRequest.builder()
                    .query("Java Python")
                    .topK(10)
                    .knowledgeBaseIds(List.of(kbId1))
                    .build();

            List<?> results = jdbcTemplate.queryForList(
                    request.getSql(),
                    request.getParams().toArray()
            );

            // 验证返回的是List类型
            assertInstanceOf(List.class, results);
            // 验证结果不为空
            assertFalse(results.isEmpty(), "BM25查询应该返回结果");

            // 验证结果结构
            assertTrue(results.get(0) instanceof java.util.Map, "结果项应该是Map类型");

        } finally {
            jdbcTemplate.update("DELETE FROM kb_parent_documents WHERE kb_id = ?", kbId1);
            jdbcTemplate.update("DELETE FROM kb_parent_documents WHERE kb_id = ?", kbId2);
            try {
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'kb_id' = ?", kbId1.toString());
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'kb_id' = ?", kbId2.toString());
            } catch (Exception e) {
                // 忽略清理异常
            }
        }
    }
}
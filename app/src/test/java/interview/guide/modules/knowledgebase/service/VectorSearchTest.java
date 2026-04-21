package interview.guide.modules.knowledgebase.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 向量查询测试
 * 验证向量检索能够正确返回结果
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("向量查询测试")
class VectorSearchTest {

    @Autowired
    private KnowledgeBaseVectorService vectorService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long TEST_KB_ID = 66666L;

    @Test
    @DisplayName("向量查询 - 验证返回List类型")
    void testVectorSearchReturnsList() {
        // 准备测试数据 - 使用简洁明确的内容
        String content = "Java是一种面向对象的编程语言，广泛应用于企业级开发。Spring框架是Java生态中最流行的企业级框架。";

        try {
            // 向量化存储
            vectorService.vectorizeAndStore(TEST_KB_ID, content);

            // 执行向量检索 - 使用与存储内容高度相似的查询
            List<Document> results = vectorService.vectorSearch(
                    "Java编程语言和Spring框架是企业级开发的主流技术",
                    List.of(TEST_KB_ID),
                    10,
                    0.0
            );

            // 验证返回的是List类型
            assertInstanceOf(List.class, results);
            // 验证结果不为空
            assertFalse(results.isEmpty(), "向量查询应该返回结果");

            // 验证返回的数据结构正确
            Document doc = results.get(0);
            assertNotNull(doc.getText(), "文档内容不应为null");
            assertNotNull(doc.getMetadata(), "元数据不应为null");

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
    @DisplayName("向量查询 - 验证知识库ID过滤")
    void testVectorSearchWithKbIdFilter() {
        Long kbId1 = 66667L;
        Long kbId2 = 66668L;

        try {
            // 向量化两个知识库
            vectorService.vectorizeAndStore(kbId1, "Java是一种面向对象的编程语言，广泛应用于企业级开发。");
            vectorService.vectorizeAndStore(kbId2, "Python是一种解释型编程语言，语法简洁易学。");

            // 查询kbId1 - 使用与存储内容相似的查询
            List<Document> results = vectorService.vectorSearch(
                    "Java编程语言在企业级开发中应用广泛",
                    List.of(kbId1),
                    10,
                    0.0
            );

            // 验证返回List类型
            assertInstanceOf(List.class, results);
            // 验证结果不为空
            assertFalse(results.isEmpty(), "向量查询应该返回结果");

            // 验证kb_id正确
            for (Document doc : results) {
                String kbId = (String) doc.getMetadata().get("kb_id");
                assertEquals(kbId1.toString(), kbId, "应该只返回指定知识库的文档");
            }

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

    @Test
    @DisplayName("向量查询 - 验证topK参数限制")
    void testVectorSearchWithTopK() {
        // 使用多段落内容 - 与集成测试相同
        String content = """
            Java核心技术包括多线程、集合框架和JVM原理。多线程编程是Java开发中的重要技能。

            Spring Boot是Java生态中最流行的企业级框架。它简化了Spring应用的开发和部署。

            微服务架构是一种软件架构风格，将大型应用拆分为多个小型独立服务。
            """;

        try {
            vectorService.vectorizeAndStore(TEST_KB_ID, content);

            // 使用与内容高度相似的查询
            List<Document> results = vectorService.vectorSearch(
                    "Java多线程编程和Spring Boot微服务架构是企业开发核心技术",
                    List.of(TEST_KB_ID),
                    20,
                    0.0
            );

            // 验证返回List类型
            assertInstanceOf(List.class, results);
            // 验证结果不为空
            assertFalse(results.isEmpty(), "向量查询应该返回结果");
            // 结果数量不应超过topK
            assertTrue(results.size() <= 20, "结果数量不应超过topK");

        } finally {
            jdbcTemplate.update("DELETE FROM kb_parent_documents WHERE kb_id = ?", TEST_KB_ID);
            try {
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'kb_id' = ?", TEST_KB_ID.toString());
            } catch (Exception e) {
                // 忽略清理异常
            }
        }
    }

    @Test
    @DisplayName("向量查询 - 验证空结果返回空List")
    void testVectorSearchWithNoResults() {
        // 执行向量检索（使用不存在的关键词，且kb不存在）
        List<Document> results = vectorService.vectorSearch(
                "不存在的关键词XYZ123",
                List.of(99999L),
                10,
                0.0
        );

        // 验证返回空List
        assertInstanceOf(List.class, results);
        assertTrue(results.isEmpty(), "不存在的关键词应返回空结果");
    }
}
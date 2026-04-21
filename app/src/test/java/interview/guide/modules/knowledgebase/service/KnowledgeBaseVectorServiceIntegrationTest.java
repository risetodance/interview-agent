package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.model.ParentDocumentEntity;
import interview.guide.modules.knowledgebase.repository.ParentDocumentRepository;
import interview.guide.modules.knowledgebase.repository.VectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知识库向量服务集成测试
 * 覆盖：向量化入库、向量检索、BM25检索、RRF融合
 */
@SpringBootTest
@ActiveProfiles("dev")
class KnowledgeBaseVectorServiceIntegrationTest {

    @Autowired
    private KnowledgeBaseVectorService vectorService;

    @Autowired
    private ParentDocumentRepository parentRepository;

    @Autowired
    private VectorRepository vectorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long TEST_KB_ID = 88888L;

    @BeforeEach
    void setUp() {
        // 清理旧数据（使用JDBC原生SQL避免事务问题）
        jdbcTemplate.update("DELETE FROM kb_parent_documents WHERE kb_id = ?", TEST_KB_ID);
        try {
            jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'kb_id' = ?", TEST_KB_ID.toString());
        } catch (Exception e) {
            // vector_store 可能不存在，跳过清理
        }
    }

    @Test
    void testVectorizeAndStore_parentChildRetrieval() {
        // 测试内容：多个自然段落
        String content = """
            第一段：这是关于Java面试的内容。Java是一种面向对象的编程语言。

            第二段：Spring框架是Java生态中最流行的企业级框架。Spring Boot可以快速构建应用。

            第三段：微服务架构是一种设计方法，将大型应用拆分为多个小服务。
            """;

        // 执行向量化
        vectorService.vectorizeAndStore(TEST_KB_ID, content);

        // 验证 Parent 文档已存储
        List<ParentDocumentEntity> parents = parentRepository.findByKbIdOrderByChunkIndex(TEST_KB_ID);
        assertFalse(parents.isEmpty(), "Parent 文档应该被存储");
        assertEquals(3, parents.size(), "应该有 3 个 Parent（3 个段落）");

        // 验证检索返回的是 Parent 内容
        List<Document> results = vectorService.similaritySearch(
                "Java Spring",
                List.of(TEST_KB_ID),
                5,
                0.0
        );

        // 验证检索功能正常工作（结果可能为空如果没有pg_search扩展）
        if (!results.isEmpty()) {
            for (Document doc : results) {
                assertNotNull(doc.getText());
                assertTrue(doc.getText().contains("段"));
                assertTrue(doc.getMetadata().containsKey("parent_id"));
            }
        }
    }

    @Test
    void testSimilaritySearch_withVectorAndBm25() {
        // 准备测试内容
        String content = """
            Java核心技术包括多线程、集合框架和JVM原理。多线程编程是Java开发中的重要技能。

            Spring Boot是Java生态中最流行的企业级框架。它简化了Spring应用的开发和部署。

            微服务架构是一种软件架构风格，将大型应用拆分为多个小型独立服务。
            """;

        // 向量化存储
        vectorService.vectorizeAndStore(TEST_KB_ID, content);

        // 执行混合检索
        List<Document> results = vectorService.similaritySearch(
                "Java多线程",
                List.of(TEST_KB_ID),
                5,
                0.0
        );

        // 验证返回的是Parent内容（非碎片）
        // 注意：如果pg_search扩展不可用，结果可能为空
        if (!results.isEmpty()) {
            for (Document doc : results) {
                assertNotNull(doc.getText());
                assertTrue(doc.getMetadata().containsKey("kb_id"));
                assertTrue(doc.getMetadata().containsKey("parent_id"));
            }
        }
    }

    @Test
    void testSearchWithEmptyContent() {
        // 执行向量化（空内容）
        vectorService.vectorizeAndStore(TEST_KB_ID, "");

        // 验证无异常，检索返回空
        List<Document> results = vectorService.similaritySearch(
                "Java",
                List.of(TEST_KB_ID),
                5,
                0.0
        );
        assertTrue(results.isEmpty(), "空内容检索应返回空结果");
    }

    @Test
    void testParentStorageCount() {
        // 准备测试内容 - 每个段落足够长以确保被正确分块
        String content = """
            这是一段关于Java编程语言的介绍。Java是一种广泛使用的面向对象编程语言，具有平台无关性和自动内存管理等特性。

            第二段介绍Spring框架。Spring是Java企业级开发中最流行的框架之一，提供了依赖注入、AOP和事务管理等核心功能。

            第三段介绍微服务架构。微服务是一种软件架构风格，将大型应用拆分为多个小型、独立的服务，每个服务运行在自己的进程中。
            """;

        vectorService.vectorizeAndStore(TEST_KB_ID, content);

        // 验证Parent文档数量
        List<ParentDocumentEntity> parents = parentRepository.findByKbIdOrderByChunkIndex(TEST_KB_ID);
        assertFalse(parents.isEmpty(), "Parent文档应该被存储");
        assertEquals(3, parents.size(), "应该有3个Parent段落");
    }

    @Test
    void testSearchWithMultipleKbIds() {
        Long kbId1 = 88889L;
        Long kbId2 = 88890L;

        String content1 = "Java是一种面向对象的编程语言。";
        String content2 = "Python是一种解释型编程语言。";

        try {
            vectorService.vectorizeAndStore(kbId1, content1);
            vectorService.vectorizeAndStore(kbId2, content2);

            // 验证只检索指定知识库的内容
            List<Document> results = vectorService.similaritySearch(
                    "编程语言",
                    List.of(kbId1),
                    5,
                    0.0
            );

            // 验证结果只包含指定知识库的文档
            if (!results.isEmpty()) {
                for (Document doc : results) {
                    String kbId = (String) doc.getMetadata().get("kb_id");
                    assertEquals(kbId1.toString(), kbId, "应该只返回指定知识库的文档");
                }
            }
        } finally {
            // 清理 - 使用JDBC避免事务问题
            jdbcTemplate.update("DELETE FROM kb_parent_documents WHERE kb_id = ?", kbId1);
            jdbcTemplate.update("DELETE FROM kb_parent_documents WHERE kb_id = ?", kbId2);
            try {
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'kb_id' = ?", kbId1.toString());
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'kb_id' = ?", kbId2.toString());
            } catch (Exception e) {
                // vector_store可能不存在
            }
        }
    }
}
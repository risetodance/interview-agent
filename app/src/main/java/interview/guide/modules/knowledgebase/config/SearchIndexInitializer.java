package interview.guide.modules.knowledgebase.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 搜索索引初始化器
 * 应用启动时创建向量搜索和全文搜索所需的扩展和索引
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 应用启动后初始化扩展和索引
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            createQuestionsFtsIndex();
            createVectorStoreBm25Index();
        } catch (Exception e) {
            log.warn("初始化搜索索引失败: {}", e.getMessage());
        }
    }


    /**
     * 创建题库 BM25 搜索索引（使用 ParadeDB pg_search 的 bm25 索引）
     */
    private void createQuestionsFtsIndex() {
        log.info("检查并创建题库 BM25 搜索索引...");

        // 检查 pg_search 扩展是否可用
        if (isPgSearchAvailable()) {
            log.warn("pg_search 扩展不可用，跳过题库 BM25 索引创建");
            return;
        }

        // 检查 questions 表是否存在
        try {
            Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'questions'",
                Integer.class
            );
            if (tableCount == null || tableCount == 0) {
                log.info("questions 表不存在，跳过题库索引创建");
                return;
            }
        } catch (Exception e) {
            log.warn("检查 questions 表失败: {}", e.getMessage());
            return;
        }

        // 创建 ParadeDB BM25 索引
        // key_field 指定主键字段用于唯一标识文档
        try {
            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_questions_bm25
                ON questions USING bm25 (id, question_bank_id, content)
                WITH (key_field = 'id')
                """);
            log.info("题库 BM25 搜索索引创建成功");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("题库 BM25 搜索索引已存在");
            } else {
                log.warn("创建题库 BM25 索引失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 创建知识库 BM25 搜索索引（使用 ParadeDB pg_search 的 bm25 索引）
     */
    private void createVectorStoreBm25Index() {
        log.info("检查并创建知识库 BM25 搜索索引...");

        // 检查 pg_search 扩展是否可用
        if (isPgSearchAvailable()) {
            log.warn("pg_search 扩展不可用，跳过知识库 BM25 索引创建");
            return;
        }

        // 检查 vector_store 表是否存在
        try {
            Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'vector_store'",
                Integer.class
            );
            if (tableCount == null || tableCount == 0) {
                log.info("vector_store 表不存在，跳过 BM25 索引创建");
                return;
            }
        } catch (Exception e) {
            log.warn("检查 vector_store 表失败: {}", e.getMessage());
            return;
        }

        // 创建 ParadeDB BM25 索引
        // key_field 指定主键字段用于唯一标识文档
        try {
            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_vector_store_bm25
                ON vector_store USING bm25 (id, content)
                WITH (key_field = 'id')
                """);
            log.info("知识库 BM25 搜索索引创建成功");
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("知识库 BM25 索引已存在");
            } else {
                log.warn("创建知识库 BM25 索引失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 检查 pg_search 扩展是否可用
     */
    private boolean isPgSearchAvailable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_search'",
                Integer.class
            );
            return count == null || count <= 0;
        } catch (Exception e) {
            log.debug("检查 pg_search 扩展失败: {}", e.getMessage());
            return true;
        }
    }
}

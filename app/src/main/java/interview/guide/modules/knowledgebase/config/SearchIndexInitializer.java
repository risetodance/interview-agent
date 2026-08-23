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
            log.error("初始化搜索索引失败: {}", e.getMessage(), e);
        }
    }


    /**
     * 创建题库 BM25 搜索索引（使用 ParadeDB pg_search 的 bm25 索引）
     */
    private void createQuestionsFtsIndex() {
        log.info("检查并创建题库 BM25 搜索索引...");

        // 检查 pg_search 扩展是否不可用，未安装时先尝试自动补装
        if (!ensurePgSearchInstalled()) {
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
            log.error("检查 questions 表失败: {}", e.getMessage(), e);
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
                log.error("创建题库 BM25 索引失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 创建知识库 BM25 搜索索引（使用 ParadeDB pg_search 的 bm25 索引）
     */
    private void createVectorStoreBm25Index() {
        log.info("检查并创建知识库 BM25 搜索索引...");

        // 检查 pg_search 扩展是否不可用，未安装时先尝试自动补装
        if (!ensurePgSearchInstalled()) {
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
            log.error("检查 vector_store 表失败: {}", e.getMessage(), e);
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
                log.error("创建知识库 BM25 索引失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 检查 pg_search 扩展是否不可用
     */
    private boolean isPgSearchUnavailable() {
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

    /**
     * 确保 pg_search 扩展已安装到当前库（幂等，未安装时自动补装）。
     * <p>
     * paradedb 镜像自带扩展文件，但"安装到库"（CREATE EXTENSION）是 per-database 动作，
     * 新环境初始化或手工建库时容易遗漏——启动时自动补装，免去手动 psql。
     * 前置条件：shared_preload_libraries 已包含 pg_search（compose 已显式声明，镜像默认配置亦含）；
     * 未预加载时 CREATE EXTENSION 会失败，此处捕获后返回 false，调用方降级跳过索引创建。
     */
    private boolean ensurePgSearchInstalled() {
        if (!isPgSearchUnavailable()) {
            return true;
        }
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_search");
            log.info("pg_search 扩展已自动安装");
            return true;
        } catch (Exception e) {
            log.warn("pg_search 扩展自动安装失败（多半是未配置 shared_preload_libraries）: {}", e.getMessage());
            return false;
        }
    }
}

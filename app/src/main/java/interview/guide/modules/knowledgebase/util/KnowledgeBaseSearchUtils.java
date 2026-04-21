package interview.guide.modules.knowledgebase.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库检索工具类
 * 封装混合检索（向量+BM25）的公共逻辑
 */
@Slf4j
public final class KnowledgeBaseSearchUtils {

    private KnowledgeBaseSearchUtils() {
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合算法
     * 将向量检索和BM25检索的结果进行融合排序
     * @param vectorResults 向量检索结果
     * @param bm25Results BM25检索结果
     * @param topK 返回前K个结果
     * @return 融合后的文档列表
     */
    public static List<Document> reciprocalRankFusion(
            List<Document> vectorResults,
            List<Document> bm25Results,
            int topK) {
        return reciprocalRankFusion(vectorResults, bm25Results, topK, 60);
    }

    /**
     * RRF融合算法重载
     * @param vectorResults 向量检索结果
     * @param bm25Results BM25检索结果
     * @param topK 返回前K个结果
     * @param rrfK RRF参数，默认60
     * @return 融合后的文档列表
     */
    public static List<Document> reciprocalRankFusion(
            List<Document> vectorResults,
            List<Document> bm25Results,
            int topK,
            int rrfK) {
        // 边界条件：两者都为空
        if (vectorResults.isEmpty() && bm25Results.isEmpty()) {
            return List.of();
        }

        // 单路为空时直接返回另一路结果
        if (vectorResults.isEmpty()) {
            return bm25Results.stream().limit(topK).collect(Collectors.toList());
        }

        if (bm25Results.isEmpty()) {
            return vectorResults.stream().limit(topK).collect(Collectors.toList());
        }

        // 初始化：scores存储融合分数，docMap存储文档ID到文档的映射
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        // 分别计算向量检索和BM25检索的RRF分数并合并
        setRrfScore(vectorResults, scores, docMap, rrfK);
        setRrfScore(bm25Results, scores, docMap, rrfK);

        // 按融合分数降序排序，返回topK个结果
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> docMap.get(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 计算单路检索结果的RRF分数
     * <p>
     * RRF公式：score = 1.0 / (rrfK + rank)
     * 其中rank是该文档在该路检索结果中的排名（从0开始）
     */
    private static void setRrfScore(List<Document> documents, Map<String, Double> scores, Map<String, Document> docMap, int rrfK) {
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String docId = getDocumentId(doc);
            // RRF分数：排名越靠前，分数越高
            double rrfScore = 1.0 / (rrfK + i + 1);
            // 累加多路检索的分数
            scores.merge(docId, rrfScore, Double::sum);
            // 保留文档信息，合并metadata
            docMap.merge(docId, doc, (doc1, doc2) -> {
                doc1.getMetadata().putAll(doc2.getMetadata());
                return doc1;
            });
        }
    }

    /**
     * 获取文档的唯一标识符
     * @param doc Spring AI Document对象
     * @return 文档ID，若无ID则使用文本hashCode
     */
    public static String getDocumentId(Document doc) {
        if (doc != null && !doc.getId().isBlank()) {
            return doc.getId();
        }
        return doc != null ? String.valueOf(doc.getText().hashCode()) : "";
    }

    /**
     * 构建 kb_id 过滤表达式
     * @param knowledgeBaseIds 知识库ID列表
     * @return Spring AI VectorStore格式的IN表达式，如 "kb_id IN ['1', '2', '3']"
     */
    public static String buildKbFilterExpression(List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return "";
        }
        // kb_id 在 metadata 中存储为字符串，因此需要用单引号包裹
        String values = knowledgeBaseIds.stream()
                .filter(Objects::nonNull)
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));
        return "kb_id IN [" + values + "]";
    }

    /**
     * 检查 pg_search 扩展是否可用（带缓存）
     */
    private static volatile Boolean pgSearchAvailableCache = null;

    public static boolean isPgSearchAvailable(JdbcTemplate jdbcTemplate) {
        // 缓存命中
        if (pgSearchAvailableCache != null) {
            return pgSearchAvailableCache;
        }
        // 双重检查锁定，确保线程安全
        synchronized (KnowledgeBaseSearchUtils.class) {
            if (pgSearchAvailableCache != null) {
                return pgSearchAvailableCache;
            }
            try {
                // 查询pg_extension表确认pg_search扩展是否已安装
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_search'",
                        Integer.class);
                pgSearchAvailableCache = count != null && count > 0;
            } catch (Exception e) {
                // 扩展不存在或查询失败，标记为不可用
                log.debug("检查 pg_search 扩展失败: {}", e.getMessage());
                pgSearchAvailableCache = false;
            }
        }
        return pgSearchAvailableCache;
    }

    /**
     * BM25搜索请求构建器
     * <p>
     * 使用Builder模式构建BM25查询SQL和参数，
     * 支持按knowledgeBaseIds进行JSONPath过滤
     */
    public static class Bm25SearchRequest {
        private String query;
        private int topK;
        private List<Long> knowledgeBaseIds;

        private Bm25SearchRequest() {
        }

        public static Bm25SearchRequest builder() {
            return new Bm25SearchRequest();
        }

        public Bm25SearchRequest query(String query) {
            // 替换特殊字符|避免BM25解析问题
            this.query = (query == null ? "" : query).replace("|", " ");
            return this;
        }

        public Bm25SearchRequest topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Bm25SearchRequest knowledgeBaseIds(List<Long> knowledgeBaseIds) {
            this.knowledgeBaseIds = knowledgeBaseIds;
            return this;
        }

        public Bm25SearchRequest build() {
            return this;
        }

        /**
         * 获取BM25查询SQL
         * 使用JSONPath语法过滤kb_id: metadata::jsonb @@ '$.kb_id == "1"'::jsonpath
         */
        public String getSql() {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, content, metadata, paradedb.score(id) AS rank ");
            sql.append("FROM vector_store ");
            sql.append("WHERE content @@@ ? ");

            // 使用 JSONPath 语法过滤 kb_id
            // 生成格式: ($.kb_id == "1" || $.kb_id == "2" || $.kb_id == "3")
            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                StringJoiner joiner = new StringJoiner(" || ", "(", ")");
                for (Long kbId : knowledgeBaseIds) {
                    joiner.add("$.kb_id == \"" + kbId + "\"");
                }
                sql.append(" AND metadata::jsonb @@ '").append(joiner).append("'::jsonpath");
            }

            sql.append(" ORDER BY rank DESC LIMIT ?");
            return sql.toString();
        }

        /**
         * 获取查询参数
         */
        public List<Object> getParams() {
            return List.of(query, topK);
        }
    }

    /**
     * BM25 结果行映射器
     */
    public static class Bm25DocumentRowMapper implements RowMapper<Document> {

        private static final String COLUMN_METADATA = "metadata";
        private static final String COLUMN_ID = "id";
        private static final String COLUMN_CONTENT = "content";

        private final ObjectMapper objectMapper;

        public Bm25DocumentRowMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
            String id = rs.getString(COLUMN_ID);
            String content = rs.getString(COLUMN_CONTENT);
            PGobject pgMetadata = rs.getObject(COLUMN_METADATA, PGobject.class);

            Map<String, Object> metadata = toMap(pgMetadata);
            metadata.put("score_source", "BM25");

            return Document.builder()
                    .id(id)
                    .text(content)
                    .metadata(metadata)
                    .build();
        }

        private Map<String, Object> toMap(PGobject pgObject) {
            try {
                return objectMapper.readValue(pgObject.getValue(), Map.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse PGobject to Map", e);
            }
        }
    }
}

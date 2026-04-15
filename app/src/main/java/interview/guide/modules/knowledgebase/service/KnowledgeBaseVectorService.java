package interview.guide.modules.knowledgebase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import interview.guide.modules.knowledgebase.repository.VectorRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库向量存储服务
 * 负责文档分块、向量化和检索
 * 实现混合检索：向量搜索 + BM25 全文搜索 + RRF 重排序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseVectorService {

    /**
     * 阿里云 DashScope Embedding API 批量大小限制
     */
    private static final int MAX_BATCH_SIZE = 10;

    /**
     * RRF 融合公式参数：k 值越小排名越靠前的结果权重越大
     */
    private static final int RRF_K = 60;

    private final VectorStore vectorStore;
    private final VectorRepository vectorRepository;
    private final JdbcTemplate jdbcTemplate;
    private TextSplitter textSplitter;
    private JsonMapper objectMapper;
    private DocumentRowMapper documentRowMapper;

    @PostConstruct
    public void init() {
        this.objectMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
        this.documentRowMapper = new DocumentRowMapper(this.objectMapper);
        this.textSplitter = new TokenTextSplitter();
    }

    private record DocumentRowMapper(ObjectMapper objectMapper) implements RowMapper<Document> {

            private static final String COLUMN_METADATA = "metadata";

            private static final String COLUMN_ID = "id";

            private static final String COLUMN_CONTENT = "content";


            private static final String RANK = "rank";

            private static final String BM25 = "BM25";


        @Override
            public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
                String id = rs.getString(COLUMN_ID);
                String content = rs.getString(COLUMN_CONTENT);
                PGobject pgMetadata = rs.getObject(COLUMN_METADATA, PGobject.class);

                Map<String, Object> metadata = toMap(pgMetadata);
                metadata.put("bm25_rank", RANK);
                metadata.put("score_source", BM25);

                return Document.builder()
                        .id(id)
                        .text(content)
                        .metadata(metadata)
                        .build();
            }

            private Map<String, Object> toMap(PGobject pgObject) {

                String source = pgObject.getValue();
                try {
                    return (Map<String, Object>) this.objectMapper.readValue(source, Map.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }

        }

    /**
     * 将知识库内容向量化并存储
     *
     * @param knowledgeBaseId 知识库ID
     * @param content         知识库文本内容
     */
    @Transactional
    public void vectorizeAndStore(Long knowledgeBaseId, String content) {
        log.info("开始向量化知识库: kbId={}, contentLength={}", knowledgeBaseId, content.length());
        try {
            // 1. 先删除该知识库的旧向量数据
            deleteByKnowledgeBaseId(knowledgeBaseId);

            // 2. 将文本分块
            List<Document> chunks = textSplitter.apply(
                    List.of(new Document(content))
            );

            log.info("文本分块完成: {} 个chunks", chunks.size());

            // 3. 为每个chunk添加metadata（知识库ID）
            // 统一使用 String 类型存储，确保查询一致性
            chunks.forEach(chunk -> chunk.getMetadata().put("kb_id", knowledgeBaseId.toString()));
            // 4. 分批向量化并存储（阿里云 DashScope API 限制 batch size <= 10）
            int totalChunks = chunks.size();
            int batchCount = (totalChunks + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE; // 向上取整
            log.info("开始分批向量化: 总共 {} 个chunks，分 {} 批处理，每批最多 {} 个",
                    totalChunks, batchCount, MAX_BATCH_SIZE);
            for (int i = 0; i < batchCount; i++) {
                int start = i * MAX_BATCH_SIZE;
                int end = Math.min(start + MAX_BATCH_SIZE, totalChunks);
                List<Document> batch = chunks.subList(start, end);
                log.debug("处理第 {}/{} 批: chunks {}-{}", i + 1, batchCount, start + 1, end);
                vectorStore.add(batch);
            }
            log.info("知识库向量化完成: kbId={}, chunks={}, batches={}",
                    knowledgeBaseId, totalChunks, batchCount);
        } catch (Exception e) {
            log.error("向量化知识库失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("向量化知识库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 基于多个知识库进行混合检索（向量搜索 + BM25 全文搜索 + RRF 重排序）
     *
     * @param query            查询文本
     * @param knowledgeBaseIds 知识库ID列表（如果为空则搜索所有）
     * @param topK             返回top K个结果
     * @param minScore         最小相似度分数
     * @return 相关文档列表
     */
    public List<Document> similaritySearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        log.info("混合检索开始: query={}, kbIds={}, topK={}, minScore={}",
                query, knowledgeBaseIds, topK, minScore);

        try {
            // 并行执行向量搜索和 BM25 搜索
            List<Document> vectorResults = vectorSearch(query, knowledgeBaseIds, topK * 3, minScore);
            List<Document> bm25Results = bm25Search(query, knowledgeBaseIds, topK * 3);

            log.info("搜索完成: 向量搜索={}条, BM25搜索={}条",
                    vectorResults.size(), bm25Results.size());

            // 使用 RRF 融合两种搜索结果
            List<Document> fusedResults = reciprocalRankFusion(vectorResults, bm25Results, topK);

            log.info("RRF 融合完成: 最终返回 {} 个结果,结果为:{}", fusedResults.size(), fusedResults);
            return fusedResults;

        } catch (Exception e) {
            log.warn("混合检索失败，回退到向量搜索: {}", e.getMessage());
            return vectorSearch(query, knowledgeBaseIds, topK, minScore);
        }
    }

    /**
     * 向量相似度搜索
     */
    private List<Document> vectorSearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(topK, 1));

            if (minScore > 0) {
                builder.similarityThreshold(minScore);
            }

            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                builder.filterExpression(buildKbFilterExpression(knowledgeBaseIds));
            }

            return vectorStore.similaritySearch(builder.build());
        } catch (Exception e) {
            log.warn("向量搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * BM25 全文搜索（使用 ParadeDB pg_search 的 @@@ 运算符）
     * 使用 SearchRequest.Builder 风格的链式调用构建查询
     */
    private List<Document> bm25Search(String query, List<Long> knowledgeBaseIds, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            // 检查 pg_search 扩展是否可用
            if (!isPgSearchAvailable()) {
                log.debug("pg_search 扩展不可用，跳过 BM25 搜索");
                return List.of();
            }

            // 使用 Builder 模式构建 BM25 查询
            Bm25SearchRequest request = Bm25SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .knowledgeBaseIds(knowledgeBaseIds);

            // 执行查询
            List<Document> documents = jdbcTemplate.query(request.getSql(), this.documentRowMapper, request.getParams().toArray());
            log.debug("BM25 搜索完成: 找到 {} 条结果", documents.size());
            return documents;

        } catch (Exception e) {
            log.error("BM25 搜索失败: ", e);
            return List.of();
        }
    }

    /**
     * BM25 搜索请求构建器
     * 使用 Builder 模式提供清晰的 SQL 和参数构建
     */
    private static class Bm25SearchRequest {
        private String query;
        private int topK;
        private List<Long> knowledgeBaseIds;

        private Bm25SearchRequest() {
        }

        public static Bm25SearchRequest builder() {
            return new Bm25SearchRequest();
        }

        public Bm25SearchRequest query(String query) {
            query = query == null ? "" : query
                    // 默认按空格分词
                    .replace("|", " ");
            this.query = query;
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

        public String getSql() {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, content, metadata, paradedb.score(id) AS rank ");
            sql.append("FROM vector_store ");
            sql.append("WHERE content @@@ ? ");

            // 使用 JSONPath 语法过滤 kb_id
            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                StringJoiner joiner = new StringJoiner(" || $.", "($.", ")");
                for (Long kbId : knowledgeBaseIds) {
                    joiner.add("kb_id == \"" + kbId + "\"");
                }
                String jsonPathCondition = joiner.toString();
                sql.append(" AND metadata::jsonb @@ '").append(jsonPathCondition).append("'::jsonpath");
            }

            sql.append(" ORDER BY rank DESC LIMIT ?");
            return sql.toString();
        }

        public List<Object> getParams() {
            List<Object> params = new ArrayList<>();
            params.add(query);
            params.add(topK);
            return params;
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
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("检查 pg_search 扩展失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * RRF (Reciprocal Rank Fusion) 融合算法
     * 将两个不同搜索方法的结果按排名融合
     */
    private List<Document> reciprocalRankFusion(List<Document> vectorResults, List<Document> bm25Results, int topK) {
        if (vectorResults.isEmpty() && bm25Results.isEmpty()) {
            return List.of();
        }

        if (vectorResults.isEmpty()) {
            return bm25Results.stream().limit(topK).collect(Collectors.toList());
        }

        if (bm25Results.isEmpty()) {
            return vectorResults.stream().limit(topK).collect(Collectors.toList());
        }

        // 为每个结果分配排名分数
        Map<String, Double> scores = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        // 向量搜索结果的 RRF 分数
        setAndMergeDocuments(vectorResults, scores, docMap);

        // BM25 搜索结果的 RRF 分数
        setAndMergeDocuments(bm25Results, scores, docMap);

        // 按分数降序排序
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> docMap.get(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void setAndMergeDocuments(List<Document> documents, Map<String, Double> scores, Map<String, Document> docMap) {
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String docId = getDocId(doc);
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scores.merge(docId, rrfScore, Double::sum);
            docMap.merge(docId, doc, (doc1, doc2) -> {
                doc1.getMetadata().putAll(doc2.getMetadata());
                return doc1;
            });
        }
    }

    /**
     * 获取文档的唯一标识符
     */
    private String getDocId(Document doc) {
        // 优先使用 id 字段
        if (!doc.getId().isBlank()) {
            return doc.getId();
        }
        // 否则使用 content 的 hash
        return String.valueOf(doc.getText().hashCode());
    }

    private String buildKbFilterExpression(List<Long> knowledgeBaseIds) {
        String values = knowledgeBaseIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));
        return "kb_id in [" + values + "]";
    }

    /**
     * 删除指定知识库的所有向量数据
     * 委托给 VectorRepository 处理
     *
     * @param knowledgeBaseId 知识库ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        try {
            vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
        } catch (Exception e) {
            log.error("删除向量数据失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
        }
    }
}


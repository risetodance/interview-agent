package interview.guide.modules.knowledgebase.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import interview.guide.modules.knowledgebase.model.ParentDocumentEntity;
import interview.guide.modules.knowledgebase.repository.ParentDocumentRepository;
import interview.guide.modules.knowledgebase.repository.VectorRepository;
import interview.guide.modules.knowledgebase.util.KnowledgeBaseSearchUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 知识库向量存储服务
 * 负责文档分块、向量化和检索
 * 实现混合检索：向量搜索 + BM25 全文搜索 + RRF 重排序
 */
@Slf4j
@Service
public class KnowledgeBaseVectorService {

    private static final int MAX_BATCH_SIZE = 10;

    private final VectorStore vectorStore;
    private final VectorRepository vectorRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SemanticChunkingService semanticChunkingService;
    private final ParentDocumentRepository parentDocumentRepository;
    private final KnowledgeBaseSearchUtils.Bm25DocumentRowMapper rowMapper;
    private final KnowledgeBaseVectorService self;

    public KnowledgeBaseVectorService(
            VectorStore vectorStore,
            VectorRepository vectorRepository,
            JdbcTemplate jdbcTemplate,
            SemanticChunkingService semanticChunkingService,
            ParentDocumentRepository parentDocumentRepository,
            @Lazy KnowledgeBaseVectorService self) {
        this.vectorStore = vectorStore;
        this.vectorRepository = vectorRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.semanticChunkingService = semanticChunkingService;
        this.parentDocumentRepository = parentDocumentRepository;
        this.rowMapper = new KnowledgeBaseSearchUtils.Bm25DocumentRowMapper(
                JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build());
        this.self = self;
    }

    /**
     * 向量化并存储知识库内容
     * <p>
     * 流程：
     * 1. 清理旧数据（向量和Parent文档）
     * 2. 按自然段落切分为Parent文档
     * 3. 存储Parent文档到数据库
     * 4. 将Parent进一步切分为Child文档
     * 5. 对Child进行向量化并存储
     * 6. 分批处理避免单次提交过大
     *
     * @param knowledgeBaseId 知识库ID
     * @param content         文档内容
     */
    @Transactional
    public void vectorizeAndStore(Long knowledgeBaseId, String content) {
        log.info("开始向量化知识库(Parent-Child): kbId={}, contentLength={}", knowledgeBaseId, content.length());
        try {
            // 1. 清理旧数据
            deleteByKnowledgeBaseId(knowledgeBaseId);
            parentDocumentRepository.deleteByKbId(knowledgeBaseId);

            // 2. 切分为Parent文档
            List<ParentDocumentEntity> parents = semanticChunkingService.splitIntoParents(knowledgeBaseId, content);
            log.info("Parent 切分完成: {} 个 parents", parents.size());

            if (parents.isEmpty()) {
                log.warn("知识库内容为空，无法向量化: kbId={}", knowledgeBaseId);
                return;
            }

            // 3. 存储Parent文档
            parentDocumentRepository.saveAll(parents);
            log.info("Parent 文档存储完成");

            // 4. 切分为Child文档（用于向量检索）
            List<Document> children = semanticChunkingService.splitIntoChildren(parents);
            log.info("Child 切分完成: {} 个 children", children.size());

            if (children.isEmpty()) {
                log.warn("Child 切分为空，无法向量化: kbId={}", knowledgeBaseId);
                return;
            }

            // 5. 分批向量化Child文档
            int totalChildren = children.size();
            int batchCount = (totalChildren + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;
            log.info("开始分批向量化: 总共 {} 个 children，分 {} 批处理",
                    totalChildren, batchCount);

            for (int i = 0; i < batchCount; i++) {
                int start = i * MAX_BATCH_SIZE;
                int end = Math.min(start + MAX_BATCH_SIZE, totalChildren);
                List<Document> batch = children.subList(start, end);
                log.debug("处理第 {}/{} 批: children {}-{}", i + 1, batchCount, start + 1, end);
                vectorStore.add(batch);
            }

            log.info("知识库向量化完成(Parent-Child): kbId={}, parents={}, children={}, batches={}",
                    knowledgeBaseId, parents.size(), totalChildren, batchCount);

        } catch (Exception e) {
            log.error("向量化知识库失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("向量化知识库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 混合检索（向量+BM25+RRF融合）
     * <p>
     * 流程：
     * 1. 并行执行向量检索和BM25检索
     * 2. 使用RRF算法融合两路结果
     * 3. 从Child关联到Parent，返回完整段落内容
     *
     * @param query            查询文本
     * @param knowledgeBaseIds 知识库ID列表（过滤用）
     * @param topK             返回前K个结果
     * @param minScore         最小相似度分数（0表示不限制）
     * @return 检索到的文档列表（Parent内容）
     */
    public List<Document> similaritySearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        log.info("混合检索开始(Parent-Child): query={}, kbIds={}, topK={}, minScore={}",
                query, knowledgeBaseIds, topK, minScore);

        try {
            // 并行执行两路检索（扩大topK获取更多候选）
            CompletableFuture<List<Document>> vectorFuture = self.vectorSearchAsync(query, knowledgeBaseIds, topK * 3, minScore);
            CompletableFuture<List<Document>> bm25Future = self.bm25SearchAsync(query, knowledgeBaseIds, topK * 3);

            CompletableFuture.allOf(vectorFuture, bm25Future).join();

            List<Document> vectorResults = vectorFuture.join();
            List<Document> bm25Results = bm25Future.join();

            log.info("Child 检索完成: 向量搜索={}条, BM25搜索={}条",
                    vectorResults.size(), bm25Results.size());

            // RRF融合排序
            List<Document> fusedChildren = KnowledgeBaseSearchUtils.reciprocalRankFusion(
                    vectorResults, bm25Results, topK);

            // 从Child关联到Parent
            List<Long> parentIds = fusedChildren.stream()
                    .map(doc -> (String) doc.getMetadata().get("parent_id"))
                    .filter(Objects::nonNull)
                    .map(Long::parseLong)
                    .distinct()
                    .toList();

            if (parentIds.isEmpty()) {
                log.info("未检索到相关 Child，返回空结果");
                return List.of();
            }

            // 查询Parent文档并转换为Document格式返回
            List<ParentDocumentEntity> parents = parentDocumentRepository.findByIdIn(parentIds);

            List<Document> result = parents.stream()
                    .map(parent -> Document.builder()
                            .text(parent.getContent())
                            .metadata(Map.of(
                                    "kb_id", parent.getKbId().toString(),
                                    "parent_id", parent.getId().toString(),
                                    "chunk_index", String.valueOf(parent.getChunkIndex())
                            ))
                            .build())
                    .toList();

            log.info("Parent-Child 检索完成: 最终返回 {} 个 parents", result.size());
            return result;

        } catch (Exception e) {
            log.warn("Parent-Child 检索失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 向量检索
     * <p>
     * 使用VectorStore的similaritySearch方法，
     * 支持按knowledgeBaseIds过滤和最小分数阈值
     */
    public List<Document> vectorSearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(topK, 1));

            // 设置最小相似度分数
            if (minScore > 0) {
                builder.similarityThreshold(minScore);
            }

            // 设置知识库ID过滤
            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                builder.filterExpression(KnowledgeBaseSearchUtils.buildKbFilterExpression(knowledgeBaseIds));
            }

            return vectorStore.similaritySearch(builder.build());
        } catch (Exception e) {
            log.warn("向量搜索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 向量检索（异步）
     */
    @Async
    public CompletableFuture<List<Document>> vectorSearchAsync(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        return CompletableFuture.completedFuture(vectorSearch(query, knowledgeBaseIds, topK, minScore));
    }

    /**
     * BM25全文检索
     * <p>
     * 使用pg_search扩展的paradedb.score()函数进行全文搜索。
     * 仅在pg_search扩展可用时执行，否则返回空结果。
     */
    private List<Document> bm25Search(String query, List<Long> knowledgeBaseIds, int topK) {
        // 边界条件：空查询直接返回
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            // 检查pg_search扩展是否可用
            if (!KnowledgeBaseSearchUtils.isPgSearchAvailable(jdbcTemplate)) {
                log.debug("pg_search 扩展不可用，跳过 BM25 搜索");
                return List.of();
            }

            // 构建BM25查询
            KnowledgeBaseSearchUtils.Bm25SearchRequest request = KnowledgeBaseSearchUtils.Bm25SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .knowledgeBaseIds(knowledgeBaseIds)
                    .build();

            List<Document> documents = jdbcTemplate.query(request.getSql(), rowMapper, request.getParams().toArray());
            log.debug("BM25 搜索完成: 找到 {} 条结果", documents.size());
            return documents;

        } catch (Exception e) {
            log.error("BM25 搜索失败: ", e);
            return List.of();
        }
    }

    /**
     * BM25全文检索（异步）
     */
    @Async
    public CompletableFuture<List<Document>> bm25SearchAsync(String query, List<Long> knowledgeBaseIds, int topK) {
        return CompletableFuture.completedFuture(bm25Search(query, knowledgeBaseIds, topK));
    }

    /**
     * 删除向量数据
     * <p>
     * 注意：此方法只删除向量存储中的数据，
     * Parent文档需要单独删除
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

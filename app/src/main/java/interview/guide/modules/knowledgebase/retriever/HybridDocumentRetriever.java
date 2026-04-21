package interview.guide.modules.knowledgebase.retriever;

import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 混合文档检索器
 * 直接委托 KnowledgeBaseVectorService.similaritySearch 实现混合检索
 * 用于 RetrievalAugmentationAdvisor
 */
@Slf4j
@Component
public class HybridDocumentRetriever implements DocumentRetriever {

    private static final int DEFAULT_TOP_K = 10;
    private static final double DEFAULT_MIN_SCORE = 0.0;

    private final KnowledgeBaseVectorService vectorService;

    public HybridDocumentRetriever(KnowledgeBaseVectorService vectorService) {
        this.vectorService = vectorService;
    }

    @NotNull
    @Override
    public List<Document> retrieve(Query query) {
        String queryText = query.text();
        if (queryText.isBlank()) {
            return List.of();
        }

        List<Long> kbIds = extractKbIds(query.context());
        int topK = extractIntParam(query.context(), "topK", DEFAULT_TOP_K);
        double minScore = extractDoubleParam(query.context(), "minScore", DEFAULT_MIN_SCORE);

        log.info("HybridDocumentRetriever 检索: query={}, kbIds={}, topK={}, minScore={}",
                queryText, kbIds, topK, minScore);

        return vectorService.similaritySearch(queryText, kbIds, topK, minScore);
    }

    private List<Long> extractKbIds(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object kbIdsObj = context.get("knowledgeBaseIds");
        if (kbIdsObj == null) {
            return null;
        }
        if (kbIdsObj instanceof List) {
            return (List<Long>) kbIdsObj;
        }
        if (kbIdsObj instanceof Long) {
            return List.of((Long) kbIdsObj);
        }
        return null;
    }

    private int extractIntParam(Map<String, Object> context, String key, int defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        Object value = context.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double extractDoubleParam(java.util.Map<String, Object> context, String key, double defaultValue) {
        if (context == null) {
            return defaultValue;
        }
        Object value = context.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

package interview.guide.modules.knowledgebase.config;

import interview.guide.modules.knowledgebase.retriever.HybridDocumentRetriever;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG Advisor 配置类
 * <p>
 * 配置混合检索（向量+BM25+RRF）的 DocumentRetriever，
 * 并使用 Spring AI 内置的 RetrievalAugmentationAdvisor 封装检索逻辑
 *
 * @see HybridDocumentRetriever
 * @see org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor
 */
@Configuration
public class RagAdvisorConfig {


    /**
     * 创建RAG检索增强Advisor
     * <p>
     * 该Advisor使用hybridDocumentRetriever进行文档检索，
     * 检索结果会自动注入到Prompt中供LLM使用
     *
     * @param hybridDocumentRetriever 混合文档检索器
     * @return RAG检索增强Advisor
     */
    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            DocumentRetriever hybridDocumentRetriever) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(hybridDocumentRetriever)
                .build();
    }
}

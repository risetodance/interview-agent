package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import interview.guide.modules.question.model.QuestionEntity;
import interview.guide.modules.question.repository.QuestionRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * 实现题库全文搜索、知识库向量搜索、Web搜索的三路并行检索 + RRF 重排序
 */
@Slf4j
@Service
public class HybridSearchService {

    private final QuestionRepository questionRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseVectorService knowledgeBaseVectorService;
    private final ToolCallbackProvider toolCallbackProvider;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ChatClient chatClient;
    private final ChatClient smallModelChatClient;

    @Autowired
    @Lazy
    private HybridSearchService self;

    @Value("classpath:prompts/web-search-system.st")
    private Resource systemPromptResource;

    @Value("classpath:prompts/web-search-user.st")
    private Resource userPromptResource;

    @Value("${app.interview.hybrid-search.topk:3}")
    private int hybridSearchTopK;

    private boolean smallModelEnabled;

    @Value("classpath:prompts/reranker-scoring-system.st")
    private Resource rerankerSystemPromptResource;

    @Value("classpath:prompts/reranker-scoring-user.st")
    private Resource rerankerUserPromptResource;

    // RRF 融合公式参数：k 值越小排名越靠前的结果权重越大
    private static final int RRF_K = 60;

    @Autowired
    public HybridSearchService(
            QuestionRepository questionRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseVectorService knowledgeBaseVectorService,
            @Autowired(required = false) ToolCallbackProvider toolCallbackProvider,
            StructuredOutputInvoker structuredOutputInvoker,
            ChatClient.Builder chatClientBuilder,
            @Value("${app.ai.small-model.model:MiniMax-M2.5}") String smallModelName,
            @Value("${app.ai.small-model.enabled:true}") boolean smallModelEnabled,
            @Value("${app.ai.small-model.temperature:0.1}") double smallModelTemperature) {
        this.questionRepository = questionRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseVectorService = knowledgeBaseVectorService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.chatClient = chatClientBuilder.build();
        this.smallModelEnabled = smallModelEnabled;
        this.smallModelChatClient = smallModelEnabled
                ? chatClientBuilder.defaultOptions(
                        ChatOptions.builder()
                                .model(smallModelName)
                                .temperature(smallModelTemperature)
                                .build())
                .build()
                : chatClient;
    }

    /**
     * 混合检索结果
     */
    @Data
    @Builder
    public static class HybridSearchResult {
        private List<RerankedItem> rerankedResults;
        private String questionBankContext;
        private String knowledgeBaseContext;
        private String webSearchContext;
        private String mergedContext;
    }

    /**
     * 重排序条目
     */
    @Data
    @AllArgsConstructor
    public static class RerankedItem {
        private String source;
        private Object original;
        private int rank;
        private double score;
    }

    /**
     * Web 搜索结果（用于 RRF）
     */
    @Data
    @AllArgsConstructor
    public static class WebSearchResult {
        private String title;
        private String snippet;
        private String url;
    }

    /**
     * Web 搜索结果列表（用于 AI 直接返回）
     */
    private record WebSearchResultList(List<WebSearchResult> results) {
    }

    /**
     * 重排序评分结果（用于小模型打分）
     */
    private record RerankerScoreResult(String source, int index, double score) {
    }

    private record RerankerScoreList(List<RerankerScoreResult> results) {
    }

    private final BeanOutputConverter<RerankerScoreList> rerankerScoreConverter =
            new BeanOutputConverter<>(RerankerScoreList.class);

    private final BeanOutputConverter<WebSearchResultList> webSearchResultConverter =
            new BeanOutputConverter<>(WebSearchResultList.class);

    /**
     * 执行混合检索（三路并行，共用 keywords）
     */
    public HybridSearchResult search(
            Long userId,
            String keywords,
            String difficulty,
            boolean enableWebSearch) {

        if (keywords == null || keywords.isBlank()) {
            return emptyResult();
        }

        // 使用配置的 topK 作为默认值
        int effectiveTopK = hybridSearchTopK;

        // 并行执行三路搜索（通过异步方法 + CompletableFuture 实现）
        CompletableFuture<List<QuestionEntity>> questionBankFuture =
                self.questionBankFullTextSearchAsync(userId, keywords, difficulty, effectiveTopK);

        CompletableFuture<List<Document>> knowledgeBaseFuture =
                self.knowledgeBaseVectorSearchAsync(userId, keywords, effectiveTopK);

        CompletableFuture<List<WebSearchResult>> webSearchFuture =
                enableWebSearch ? self.webSearchAsync(keywords, effectiveTopK)
                        : CompletableFuture.completedFuture(List.of());

        // 等待所有搜索完成
        CompletableFuture.allOf(
                questionBankFuture,
                knowledgeBaseFuture,
                webSearchFuture
        ).join();

        List<QuestionEntity> questionBankResults = questionBankFuture.join();
        List<Document> knowledgeBaseResults = knowledgeBaseFuture.join();
        List<WebSearchResult> webSearchResults = webSearchFuture.join();

        log.info("混合检索完成: 题库={}条, 知识库={}条, Web={}条",
                questionBankResults.size(), knowledgeBaseResults.size(), webSearchResults.size());

        // 构建原始上下文
        String questionBankContext = buildQuestionBankContext(questionBankResults);
        String knowledgeBaseContext = buildKnowledgeBaseContext(knowledgeBaseResults);
        String webSearchContext = buildWebSearchContext(webSearchResults);

        // 归一化 + 重排序（RRF）
        List<RerankedItem> rerankedResults = reciprocalRankFusion(
                questionBankResults, knowledgeBaseResults, webSearchResults, effectiveTopK);

        // 构建合并上下文（按重排序顺序）
        String mergedContext = buildMergedContext(rerankedResults);
        log.info("mergedContext={}", mergedContext);

        return HybridSearchResult.builder()
                .rerankedResults(rerankedResults)
                .questionBankContext(questionBankContext)
                .knowledgeBaseContext(knowledgeBaseContext)
                .webSearchContext(webSearchContext)
                .mergedContext(mergedContext)
                .build();
    }

    /**
     * 题库全文搜索（异步）
     */
    @Async
    public CompletableFuture<List<QuestionEntity>> questionBankFullTextSearchAsync(
            Long userId, String keywords, String difficulty, int topK) {
        if (keywords == null || keywords.isBlank() || userId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            List<QuestionEntity> results;
            if (difficulty != null && !difficulty.isBlank()) {
                results = questionRepository.fullTextSearchWithDifficulty(userId, keywords, difficulty, topK);
            } else {
                results = questionRepository.fullTextSearch(userId, keywords, topK);
            }
            return CompletableFuture.completedFuture(results);
        } catch (Exception e) {
            log.warn("题库全文搜索失败: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * 知识库向量搜索（异步）
     */
    @Async
    public CompletableFuture<List<Document>> knowledgeBaseVectorSearchAsync(
            Long userId, String keywords, int topK) {
        if (keywords == null || keywords.isBlank() || userId == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            List<Long> kbIds = knowledgeBaseRepository.findIdsByUserId(userId);
            List<Document> results = knowledgeBaseVectorService.similaritySearch(keywords, kbIds, topK, 0.5);
            return CompletableFuture.completedFuture(results);
        } catch (Exception e) {
            log.warn("知识库向量搜索失败: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * Web 搜索（异步，使用 MCP 工具 + structuredOutputInvoker，AI 直接返回 List<WebSearchResult>）
     */
    @Async
    public CompletableFuture<List<WebSearchResult>> webSearchAsync(String keywords, int topK) {
        if (keywords == null || keywords.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            // 1. 从 ToolCallbackProvider 获取 web_search 工具
            ToolCallback webSearchCallback = null;
            if (toolCallbackProvider != null) {
                for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
                    if ("web_search".equals(callback.getToolDefinition().name())) {
                        webSearchCallback = callback;
                        break;
                    }
                }
            }

            if (webSearchCallback == null) {
                log.warn("web_search tool not found in ToolCallbackProvider");
                return CompletableFuture.completedFuture(List.of());
            }

            // 2. 构建提示词
            String systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String userPromptTemplate = userPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String userPrompt = userPromptTemplate
                    .replace("{{keywords}}", keywords)
                    .replace("{{topK}}", String.valueOf(topK));

            // 3. 使用 structuredOutputInvoker.invoke() 调用 MCP 工具，AI 直接返回 WebSearchResultList
            WebSearchResultList resultList = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPrompt,
                    userPrompt,
                    webSearchResultConverter,
                    ErrorCode.AI_SERVICE_ERROR,
                    "Web搜索失败",
                    "HybridSearchService",
                    log,
                    webSearchCallback
            );

            List<WebSearchResult> results = resultList != null && resultList.results() != null
                    ? resultList.results()
                    : List.of();

            log.debug("Web search completed: {} results", results.size());
            return CompletableFuture.completedFuture(results);

        } catch (Exception e) {
            log.warn("Web 搜索失败: {}", e.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * 使用小模型进行相关性打分并重排序
     */
    private List<RerankedItem> reciprocalRankFusion(
            List<QuestionEntity> questionBankResults,
            List<Document> knowledgeBaseResults,
            List<WebSearchResult> webSearchResults,
            int topK) {

        List<RerankedItem> allItems = new ArrayList<>();

        // 如果没有结果，直接返回空列表
        if (questionBankResults.isEmpty() && knowledgeBaseResults.isEmpty() && webSearchResults.isEmpty()) {
            return allItems;
        }

        // 构建待评分列表
        StringBuilder candidatesBuilder = new StringBuilder();
        List<RerankedItem> pendingItems = new ArrayList<>();

        // 题库结果
        for (QuestionEntity q : questionBankResults) {
            candidatesBuilder.append(String.format("[%d] 题库题目：%s\n答案：%s\n\n",
                    pendingItems.size(), q.getContent(), q.getAnswer() != null ? q.getAnswer() : "无"));
            pendingItems.add(new RerankedItem("questionBank", q, 0, 0.0));
        }

        // 知识库结果
        for (Document d : knowledgeBaseResults) {
            candidatesBuilder.append(String.format("[%d] 知识库内容：%s\n\n",
                    pendingItems.size(), d.getText()));
            pendingItems.add(new RerankedItem("knowledgeBase", d, 0, 0.0));
        }

        // Web 搜索结果
        for (WebSearchResult w : webSearchResults) {
            candidatesBuilder.append(String.format("[%d] 网络搜索：%s\n%s\n来源：%s\n\n",
                    pendingItems.size(), w.getTitle(), w.getSnippet(), w.getUrl()));
            pendingItems.add(new RerankedItem("webSearch", w, 0, 0.0));
        }

        // 如果启用了小模型且小模型可用，则使用小模型打分
        if (smallModelEnabled && smallModelChatClient != null) {
            try {
                List<RerankerScoreResult> scores = scoreWithSmallModel(candidatesBuilder.toString());
                // 应用分数到对应项
                for (RerankerScoreResult scoreResult : scores) {
                    if (scoreResult.index() >= 0 && scoreResult.index() < pendingItems.size()) {
                        RerankedItem item = pendingItems.get(scoreResult.index());
                        item.setScore(scoreResult.score());
                        item.setRank(scoreResult.index() + 1);
                        allItems.add(item);
                    }
                }
                log.info("小模型打分完成: 共 {} 条结果", scores.size());
            } catch (Exception e) {
                log.warn("小模型打分失败，使用默认 RRF 分数: {}", e.getMessage());
                // 打分失败时使用默认 RRF 分数
                applyDefaultRrfScores(pendingItems, allItems);
            }
        } else {
            // 未启用小模型时使用默认 RRF 分数
            applyDefaultRrfScores(pendingItems, allItems);
        }

        // 按分数降序排序，取 topK
        return allItems.stream()
                .sorted(Comparator.comparingDouble(RerankedItem::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 使用小模型对候选结果进行相关性打分
     */
    private List<RerankerScoreResult> scoreWithSmallModel(String candidates) throws Exception {
        String systemPrompt = rerankerSystemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        String userPromptTemplate = rerankerUserPromptResource.getContentAsString(StandardCharsets.UTF_8);
        String userPrompt = userPromptTemplate.replace("{{candidates}}", candidates);

        RerankerScoreList scoreList = structuredOutputInvoker.invoke(
                smallModelChatClient,
                systemPrompt,
                userPrompt,
                rerankerScoreConverter,
                ErrorCode.AI_SERVICE_ERROR,
                "小模型打分失败",
                "HybridSearchService",
                log
        );

        if (scoreList == null || scoreList.results() == null) {
            throw new Exception("小模型返回为空");
        }
        return scoreList.results();
    }

    /**
     * 应用默认 RRF 分数（当小模型不可用时）
     */
    private void applyDefaultRrfScores(List<RerankedItem> pendingItems, List<RerankedItem> allItems) {
        for (int i = 0; i < pendingItems.size(); i++) {
            RerankedItem item = pendingItems.get(i);
            double rrfScore = 1.0 / (RRF_K + i + 1);
            item.setScore(rrfScore);
            item.setRank(i + 1);
            allItems.add(item);
        }
    }

    /**
     * 构建合并上下文（按重排序顺序）
     */
    private String buildMergedContext(List<RerankedItem> rerankedResults) {
        if (rerankedResults == null || rerankedResults.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        Set<String> added = new HashSet<>();

        for (RerankedItem item : rerankedResults) {
            String content = getItemContent(item);
            if (content != null && !added.contains(content)) {
                if (!sb.isEmpty()) sb.append("\n\n");
                sb.append(String.format("【%s-%d】%s",
                        getSourceLabel(item.getSource()), item.getRank(), content));
                added.add(content);
            }
        }

        return sb.toString();
    }

    private String getItemContent(RerankedItem item) {
        return switch (item.getSource()) {
            case "questionBank" -> {
                QuestionEntity q = (QuestionEntity) item.getOriginal();
                yield String.format("%s\n答案要点：%s", q.getContent(),
                        q.getAnswer() != null ? q.getAnswer() : "无");
            }
            case "knowledgeBase" -> ((org.springframework.ai.document.Document) item.getOriginal()).getText();
            case "webSearch" -> {
                WebSearchResult w = (WebSearchResult) item.getOriginal();
                yield String.format("%s\n%s\n来源：%s", w.getTitle(), w.getSnippet(), w.getUrl());
            }
            default -> null;
        };
    }

    private String getSourceLabel(String source) {
        return switch (source) {
            case "questionBank" -> "题库题目";
            case "knowledgeBase" -> "知识库内容";
            case "webSearch" -> "网络搜索";
            default -> source;
        };
    }

    /**
     * 构建题库上下文
     */
    private String buildQuestionBankContext(List<QuestionEntity> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        return results.stream()
                .map(q -> String.format("【题库题目】%s\n答案要点：%s",
                        q.getContent(), q.getAnswer() != null ? q.getAnswer() : "无"))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 构建知识库上下文
     */
    private String buildKnowledgeBaseContext(List<Document> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        return results.stream()
                .map(d -> String.format("【知识库内容】%s", d.getText()))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 构建 Web 搜索上下文
     */
    private String buildWebSearchContext(List<WebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        return results.stream()
                .map(w -> String.format("【网络搜索】%s\n%s\n来源：%s",
                        w.getTitle(), w.getSnippet(), w.getUrl()))
                .collect(Collectors.joining("\n\n"));
    }

    private HybridSearchResult emptyResult() {
        return HybridSearchResult.builder()
                .rerankedResults(List.of())
                .questionBankContext("")
                .knowledgeBaseContext("")
                .webSearchContext("")
                .mergedContext("")
                .build();
    }
}

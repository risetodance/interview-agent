package interview.guide.modules.knowledgebase.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.model.QueryRequest;
import interview.guide.modules.knowledgebase.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库查询服务
 * 基于 RetrievalAugmentationAdvisor 的 RAG 问答，支持 Query Rewrite
 */
@Slf4j
@Service
public class KnowledgeBaseQueryService {
    private static final String NO_RESULT_RESPONSE = "抱歉，在选定的知识库中未检索到相关信息。请换一个更具体的关键词或补充上下文后再试。";
    private static final Pattern SHORT_TOKEN_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_-]{2,20}$");
    private static final int STREAM_PROBE_CHARS = 120;

    private final ChatClient chatClient;
    private final KnowledgeBaseListService listService;
    private final KnowledgeBaseCountService countService;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate rewritePromptTemplate;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final BeanOutputConverter<RewrittenQuestion> outputConverter;
    private final boolean rewriteEnabled;
    private final int shortQueryLength;
    private final int topkShort;
    private final int topkMedium;
    private final int topkLong;
    private final double minScoreShort;
    private final double minScoreDefault;

    private record RewrittenQuestion(String rewrittenQuestion) {
    }

    public KnowledgeBaseQueryService(
            ChatClient.Builder chatClientBuilder,
            KnowledgeBaseListService listService,
            KnowledgeBaseCountService countService,
            RetrievalAugmentationAdvisor retrievalAugmentationAdvisor,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/knowledgebase-query-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/knowledgebase-query-rewrite.st") Resource rewritePromptResource,
            @Value("${app.ai.rag.rewrite.enabled:true}") boolean rewriteEnabled,
            @Value("${app.ai.rag.search.short-query-length:4}") int shortQueryLength,
            @Value("${app.ai.rag.search.topk-short:20}") int topkShort,
            @Value("${app.ai.rag.search.topk-medium:12}") int topkMedium,
            @Value("${app.ai.rag.search.topk-long:8}") int topkLong,
            @Value("${app.ai.rag.search.min-score-short:0.18}") double minScoreShort,
            @Value("${app.ai.rag.search.min-score-default:0.28}") double minScoreDefault) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.listService = listService;
        this.countService = countService;
        this.retrievalAugmentationAdvisor = retrievalAugmentationAdvisor;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.rewritePromptTemplate = new PromptTemplate(rewritePromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(RewrittenQuestion.class);
        this.rewriteEnabled = rewriteEnabled;
        this.shortQueryLength = shortQueryLength;
        this.topkShort = topkShort;
        this.topkMedium = topkMedium;
        this.topkLong = topkLong;
        this.minScoreShort = minScoreShort;
        this.minScoreDefault = minScoreDefault;
    }

    /**
     * 基于单个知识库回答用户问题
     */
    public String answerQuestion(Long knowledgeBaseId, String question) {
        return answerQuestion(List.of(knowledgeBaseId), question);
    }

    /**
     * 基于多个知识库回答用户问题（RAG）
     */
    public String answerQuestion(List<Long> knowledgeBaseIds, String question) {
        log.info("收到知识库提问: kbIds={}, question={}", knowledgeBaseIds, question);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizeQuestion(question).isBlank()) {
            return NO_RESULT_RESPONSE;
        }

        // 1. 验证知识库是否存在并更新问题计数
        countService.updateQuestionCounts(knowledgeBaseIds);

        // 2. 解析检索参数
        SearchParams searchParams = resolveSearchParams(question);

        // 3. Query rewrite
        String rewrittenQuestion = rewriteQuestion(question);

        try {
            // 4. 使用 Advisor 模式调用 AI（检索和上下文注入由 Advisor 自动完成）
            String answer = chatClient.prompt()
                    .system(systemPromptTemplate.render())
                    .user(rewrittenQuestion)
                    .advisors(a -> a
                            .param("knowledgeBaseIds", knowledgeBaseIds)
                            .param("topK", searchParams.topK())
                            .param("minScore", searchParams.minScore())
                    )
                    .advisors(retrievalAugmentationAdvisor)
                    .call()
                    .content();

            answer = normalizeAnswer(answer);
            log.info("知识库问答完成: kbIds={}", knowledgeBaseIds);
            return answer;

        } catch (Exception e) {
            log.error("知识库问答失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED, "知识库查询失败：" + e.getMessage());
        }
    }

    /**
     * 查询知识库并返回完整响应
     */
    public QueryResponse queryKnowledgeBase(QueryRequest request) {
        String answer = answerQuestion(request.knowledgeBaseIds(), request.question());

        // 获取知识库名称
        List<String> kbNames = listService.getKnowledgeBaseNames(request.knowledgeBaseIds());
        String kbNamesStr = String.join("、", kbNames);
        Long primaryKbId = request.knowledgeBaseIds().getFirst();

        return new QueryResponse(answer, primaryKbId, kbNamesStr);
    }

    /**
     * 流式查询知识库（SSE）
     */
    public Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question) {
        log.info("收到知识库流式提问: kbIds={}, question={}", knowledgeBaseIds, question);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizeQuestion(question).isBlank()) {
            return Flux.just(NO_RESULT_RESPONSE);
        }

        try {
            // 1. 验证知识库是否存在并更新问题计数
            countService.updateQuestionCounts(knowledgeBaseIds);

            // 2. 解析检索参数
            SearchParams searchParams = resolveSearchParams(question);

            // 3. Query rewrite
            String rewrittenQuestion = rewriteQuestion(question);

            // 4. 使用 Advisor 模式流式调用 AI
            Flux<String> responseFlux = chatClient.prompt()
                    .system(systemPromptTemplate.render())
                    .user(rewrittenQuestion)
                    .advisors(a -> a
                            .param("knowledgeBaseIds", knowledgeBaseIds)
                            .param("topK", searchParams.topK())
                            .param("minScore", searchParams.minScore())
                    )
                    .advisors(retrievalAugmentationAdvisor)
                    .stream()
                    .content();

            log.info("开始流式输出知识库回答: kbIds={}", knowledgeBaseIds);
            return normalizeStreamOutput(responseFlux)
                    .doOnComplete(() -> log.info("流式输出完成: kbIds={}", knowledgeBaseIds))
                    .onErrorResume(e -> {
                        log.error("流式输出失败: kbIds={}, error={}", knowledgeBaseIds, e.getMessage(), e);
                        return Flux.just("【错误】知识库查询失败：AI服务暂时不可用，请稍后重试。");
                    });

        } catch (Exception e) {
            log.error("知识库流式问答失败: {}", e.getMessage(), e);
            return Flux.just("【错误】知识库查询失败：" + e.getMessage());
        }
    }

    private String rewriteQuestion(String question) {
        if (!rewriteEnabled || question.isBlank()) {
            return question;
        }
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            String rewritePrompt = rewritePromptTemplate.render(variables);
            String systemPromptWithFormat = outputConverter.getFormat();

            RewrittenQuestion dto = structuredOutputInvoker.invoke(
                chatClient,
                systemPromptWithFormat,
                rewritePrompt,
                outputConverter,
                ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED,
                "Query rewrite 失败：",
                "结构化Query rewrite",
                log
            );
            String rewritten = dto.rewrittenQuestion();
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String normalized = rewritten.trim();
            log.info("Query rewrite: origin='{}', rewritten='{}'", question, normalized);
            return normalized;
        } catch (Exception e) {
            log.warn("Query rewrite 失败，使用原问题继续检索: {}", e.getMessage(), e);
            return question;
        }
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim();
    }

    private SearchParams resolveSearchParams(String question) {
        int compactLength = question.replaceAll("\\s+", "").length();
        if (compactLength <= shortQueryLength) {
            return new SearchParams(topkShort, minScoreShort);
        }
        if (compactLength <= 12) {
            return new SearchParams(topkMedium, minScoreDefault);
        }
        return new SearchParams(topkLong, minScoreDefault);
    }

    private String normalizeAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return NO_RESULT_RESPONSE;
        }
        String normalized = answer.trim();
        if (isNoResultLike(normalized)) {
            return NO_RESULT_RESPONSE;
        }
        return normalized;
    }

    private boolean isNoResultLike(String text) {
        return text.contains("没有找到相关信息")
            || text.contains("未检索到相关信息")
            || text.contains("信息不足")
            || text.contains("超出知识库范围")
            || text.contains("无法根据提供内容回答");
    }

    /**
     * 流式输出归一化
     */
    private Flux<String> normalizeStreamOutput(Flux<String> rawFlux) {
        return Flux.create(sink -> {
            StringBuilder probeBuffer = new StringBuilder();
            AtomicBoolean passthrough = new AtomicBoolean(false);
            AtomicBoolean completed = new AtomicBoolean(false);
            final Disposable[] disposableRef = new Disposable[1];

            disposableRef[0] = rawFlux.subscribe(
                chunk -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (passthrough.get()) {
                        sink.next(chunk);
                        return;
                    }

                    probeBuffer.append(chunk);
                    String probeText = probeBuffer.toString();
                    if (isNoResultLike(probeText)) {
                        completed.set(true);
                        sink.next(NO_RESULT_RESPONSE);
                        sink.complete();
                        if (disposableRef[0] != null) {
                            disposableRef[0].dispose();
                        }
                        return;
                    }

                    if (probeBuffer.length() >= STREAM_PROBE_CHARS) {
                        passthrough.set(true);
                        sink.next(probeText);
                        probeBuffer.setLength(0);
                    }
                },
                sink::error,
                () -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (!passthrough.get()) {
                        sink.next(normalizeAnswer(probeBuffer.toString()));
                    }
                    sink.complete();
                }
            );

            sink.onCancel(() -> {
                if (disposableRef[0] != null) {
                    disposableRef[0].dispose();
                }
            });
        });
    }

    private record SearchParams(int topK, double minScore) {
    }
}
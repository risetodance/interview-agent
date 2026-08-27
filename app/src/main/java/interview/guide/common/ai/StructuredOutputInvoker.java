package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 统一封装结构化输出调用与重试策略。
 */
@Component
public class StructuredOutputInvoker {

    private static final String STRICT_JSON_INSTRUCTION = """
            请仅返回可被 JSON 解析器直接解析的 JSON 对象，并严格满足字段结构要求：
            1) 不要输出 Markdown 代码块（如 ```json）。
            2) 不要输出任何解释文字、前后缀、注释。
            3) 所有字符串内引号必须正确转义。
            4) 绝对不要在文本中插入反斜杠转义非ASCII字符。
            """;

    /**
     * 校验结果
     */
    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult pass() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * 输出校验器接口
     */
    @FunctionalInterface
    public interface OutputValidator<T> {
        /**
         * 校验输出是否有效
         *
         * @param result AI 返回的结果
         * @return 校验结果，包含是否通过及具体信息
         */
        ValidationResult validate(T result);

        /**
         * 空实现校验器（始终返回 true）
         */
        static <T> OutputValidator<T> noOp() {
            return result -> ValidationResult.pass();
        }
    }

    public StructuredOutputInvoker(
            @Value("${app.ai.structured-max-attempts:3}") int maxAttempts,
            @Value("${app.ai.structured-include-last-error:true}") boolean includeLastErrorInRetryPrompt
    ) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.includeLastErrorInRetryPrompt = includeLastErrorInRetryPrompt;
    }

    private final int maxAttempts;
    private final boolean includeLastErrorInRetryPrompt;

    public <T> T invoke(
            ChatClient chatClient,
            String systemPromptWithFormat,
            String userPrompt,
            BeanOutputConverter<T> outputConverter,
            ErrorCode errorCode,
            String errorPrefix,
            String logContext,
            Logger log,
            ToolCallback... toolCallbacks
    ) {
        return invoke(chatClient, systemPromptWithFormat, userPrompt, outputConverter,
                errorCode, errorPrefix, logContext, log, OutputValidator.noOp(), toolCallbacks);
    }

    public <T> T invoke(
            ChatClient chatClient,
            String systemPromptWithFormat,
            String userPrompt,
            BeanOutputConverter<T> outputConverter,
            ErrorCode errorCode,
            String errorPrefix,
            String logContext,
            Logger log,
            OutputValidator<T> validator,
            ToolCallback... toolCallbacks
    ) {
        return doInvoke(chatClient, systemPromptWithFormat, userPrompt,
                ChatClient.ChatClientRequestSpec::user,
                toolCallbacks, outputConverter, validator, errorCode, errorPrefix, logContext, log);
    }

    /**
     * 多模态结构化输出调用：user prompt 文本（防注入边界包裹）与图片等 Media 一同发送。
     * <p>与 {@link #invoke} 共用重试 / 校验 / 防注入策略，仅 user 消息构建方式不同
     * （{@code .user(spec -> spec.text(...).media(...))}），用于视觉识图等场景。
     *
     * @param media 图片等媒体列表（与 userPrompt 同一条消息发送，可为 null / 空）
     */
    public <T> T invokeWithMedia(
            ChatClient chatClient,
            String systemPromptWithFormat,
            String userPrompt,
            List<Media> media,
            BeanOutputConverter<T> outputConverter,
            ErrorCode errorCode,
            String errorPrefix,
            String logContext,
            Logger log
    ) {
        Media[] mediaArray = media == null ? new Media[0] : media.toArray(new Media[0]);
        return doInvoke(chatClient, systemPromptWithFormat, userPrompt,
                (spec, wrappedUserPrompt) -> spec.user(
                        userSpec -> userSpec.text(wrappedUserPrompt).media(mediaArray)),
                new ToolCallback[0], outputConverter, OutputValidator.noOp(),
                errorCode, errorPrefix, logContext, log);
    }

    /**
     * 重试策略核心循环：user 消息构建方式由 userApplier 决定（纯文本 / 文本+Media），
     * 其余（attempt system prompt 重建、防注入边界、结构化解析、校验）两条入口完全一致。
     */
    private <T> T doInvoke(
            ChatClient chatClient,
            String systemPromptWithFormat,
            String userPrompt,
            BiConsumer<ChatClient.ChatClientRequestSpec, String> userApplier,
            ToolCallback[] toolCallbacks,
            BeanOutputConverter<T> outputConverter,
            OutputValidator<T> validator,
            ErrorCode errorCode,
            String errorPrefix,
            String logContext,
            Logger log
    ) {
        // 过滤掉 toolCallbacks 中的 null 元素：某些调用方（如 SingleAnswerEvaluationService）
        // 在 web_search 工具不可用（MCP 未启用 / provider 无此工具）时会传 null，
        // Spring AI 的 toolCallbacks() 会因 null 元素抛 IllegalArgumentException，这里统一兜底。
        ToolCallback[] effectiveToolCallbacks = toolCallbacks == null ? new ToolCallback[0]
                : java.util.Arrays.stream(toolCallbacks)
                .filter(java.util.Objects::nonNull)
                .toArray(ToolCallback[]::new);

        Exception lastError = null;
        String lastValidationMessage = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String attemptSystemPrompt = attempt == 1
                    ? systemPromptWithFormat
                    : buildRetrySystemPrompt(systemPromptWithFormat, lastError, lastValidationMessage);
            // 每次调用（含重试）生成不可预测的 boundaryId，攻击者无法提前构造闭合标签
            String boundaryId = PromptSecurityConstants.generateBoundaryId();
            try {
                ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                        .system(attemptSystemPrompt + PromptSecurityConstants.buildAntiInjectionInstruction(boundaryId));
                String wrappedUserPrompt = PromptSecurityConstants.wrap(
                        PromptSecurityConstants.stripBoundaryTags(userPrompt), boundaryId);
                userApplier.accept(spec, wrappedUserPrompt);
                T result = spec.toolCallbacks(effectiveToolCallbacks)
                        .call()
                        .entity(outputConverter);

                // 校验结果
                ValidationResult validation = validator.validate(result);
                if (validation.valid()) {
                    return result;
                } else {
                    lastValidationMessage = validation.message();
                    log.warn("{}结构化输出校验失败: attempt={}, reason={}", logContext, attempt, lastValidationMessage);
                    lastError = new BusinessException(errorCode, "结构化输出校验失败: " + lastValidationMessage);
                }
            } catch (Exception e) {
                lastError = e;
                log.error("{}结构化解析失败，准备重试: attempt={}, error={}", logContext, attempt, e.getMessage(), e);
            }
        }

        throw new BusinessException(
                errorCode,
                errorPrefix + (lastError != null ? lastError.getMessage() : "unknown")
        );
    }

    private String buildRetrySystemPrompt(String systemPromptWithFormat, Exception lastError, String validationMessage) {
        StringBuilder prompt = new StringBuilder(systemPromptWithFormat)
                .append("\n\n")
                .append(STRICT_JSON_INSTRUCTION);

        if (validationMessage != null && !validationMessage.isBlank()) {
            prompt.append("\n上次输出校验失败原因：").append(validationMessage);
            prompt.append("\n请根据校验失败原因重新输出符合要求的 JSON。");
        } else {
            prompt.append("\n上次输出解析失败，请仅返回合法 JSON。");
        }

        if (includeLastErrorInRetryPrompt && lastError != null && lastError.getMessage() != null) {
            prompt.append("\n上次失败原因：")
                    .append(sanitizeErrorMessage(lastError.getMessage()));
        }
        return prompt.toString();
    }

    private String sanitizeErrorMessage(String message) {
        String oneLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 200) {
            return oneLine.substring(0, 200) + "...";
        }
        return oneLine;
    }
}

package interview.guide.modules.aimodel.model;

/**
 * AI 模型端点信息（构建底层 ChatModel 的最小契约）
 * <p>只携带构建 {@code OpenAiChatModel} 所需的 4 个字段，避免把完整的 {@link AiModelConfigEntity}
 * （持久化对象，含 id / 时间戳 / 审计字段）当作传输契约泄漏给构建方法。
 * ProbeService 的临时测试场景原本只能构造一个半 Entity（其余字段全 null），此处以 record 收敛契约边界。
 *
 * @param baseUrl     OpenAI 兼容 base URL
 * @param apiKey      明文 api key
 * @param modelName   模型名
 * @param temperature 采样温度（可为 null，由调用方决定是否填充）
 */
public record AiModelEndpoint(String baseUrl, String apiKey, String modelName, Double temperature) {
}

package interview.guide.modules.aimodel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.aimodel.model.AiModelConfigEntity;
import interview.guide.modules.aimodel.model.AiModelEndpoint;
import interview.guide.modules.aimodel.model.ProbeRequest;
import interview.guide.modules.aimodel.model.ProbeResult;
import interview.guide.modules.aimodel.model.TestResult;
import interview.guide.modules.aimodel.repository.AiModelConfigRepository;
import interview.guide.modules.aimodel.util.AiHttpClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 模型探针服务
 * <p>负责拉取可用模型列表（probe）与连接测试（test）。
 * 两者均不抛异常：成功返回 ok=true，失败返回 ok=false + message，前端友好提示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelProbeService {

    private final AiModelRegistry aiModelRegistry;
    private final AiModelConfigRepository aiModelConfigRepository;

    /**
     * JSON 解析（probe 解析 /models 响应）
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * probe 超时（拉取 /models）
     */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);
    /**
     * test 超时（极简 chat 调用）
     */
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(20);

    /**
     * 拉取可用模型列表
     * <p>用 RestClient 打 {baseUrl}/models（baseUrl 已规范化为完整 URL，含版本前缀），Header Authorization: Bearer {apiKey}，
     * 解析 OpenAI 标准响应 {data:[{id,...}]}。
     * <p>优先级：req.id 非空 → 用已存配置的 baseUrl + apiKey 拉取（编辑态无需重填 key）；
     * 否则用 req.baseUrl + req.apiKey 拉取（新建态）。
     *
     * @param req 拉取请求（id 或 baseUrl+apiKey 二选一）
     * @return 拉取结果（失败时 ok=false）
     */
    public ProbeResult probe(ProbeRequest req) {
        String baseUrl = req.getBaseUrl();
        String apiKey = req.getApiKey();
        if (req.getId() != null) {
            AiModelConfigEntity stored = aiModelConfigRepository.findById(req.getId()).orElse(null);
            if (stored == null) {
                return ProbeResult.builder().models(List.of()).ok(false).message("配置不存在").build();
            }
            baseUrl = stored.getBaseUrl();
            apiKey = stored.getApiKey();
        }
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            return ProbeResult.builder().models(List.of()).ok(false).message("缺少 baseUrl / apiKey").build();
        }
        try {
            // 规范化为完整 URL（含版本前缀）后再拼 /models：编辑态从已存配置取的 baseUrl 已含版本；
            // 新建态用户可能只填域名根，按默认规则补 /v1（已含 /v数字 则不补），保证 /models 不再依赖隐式 /v1。
            String normalized = AiHttpClientFactory.normalizeForStorage(baseUrl, false);
            RestClient client = AiHttpClientFactory.restClientBuilder(PROBE_TIMEOUT, PROBE_TIMEOUT)
                    .baseUrl(AiHttpClientFactory.normalizeBaseUrl(normalized))
                    .build();
            // RestClient 无法直接反序列化为抽象类型 JsonNode（Jackson 缺无参构造），
            // 先拿 String 再用 ObjectMapper 解析，规避 "Cannot construct instance of JsonNode" 错误。
            String raw = client.get()
                    .uri("/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(String.class);
            JsonNode body = raw == null ? null : objectMapper.readTree(raw);
            List<ProbeResult.ModelInfo> models = new ArrayList<>();
            if (body != null && body.has("data") && body.get("data").isArray()) {
                for (JsonNode item : body.get("data")) {
                    String id = item.has("id") ? item.get("id").asText() : null;
                    models.add(ProbeResult.ModelInfo.builder()
                            .id(id)
                            .name(item.hasNonNull("name") ? item.get("name").asText() : id)
                            .build());
                }
            }
            return ProbeResult.builder()
                    .models(models)
                    .ok(true)
                    .message("拉取到 " + models.size() + " 个模型")
                    .build();
        } catch (Exception e) {
            log.error("拉取模型列表失败: baseUrl={}, err={}", baseUrl, e.getMessage(), e);
            return ProbeResult.builder()
                    .models(List.of())
                    .ok(false)
                    .message("拉取失败：" + rootMessage(e))
                    .build();
        }
    }

    /**
     * 测试连接
     * <p>优先用已存配置（id）测试，成功 / 失败均回写 last_test_at / last_test_ok；
     * 若无 id，则用临时 baseUrl/apiKey/modelName 构造一次性 ChatModel 测试。
     *
     * @return 测试结果（含延迟）
     */
    @Transactional
    public TestResult test(Long id, String baseUrl, String apiKey, String modelName) {
        AiModelConfigEntity config = null;
        if (id != null) {
            config = aiModelConfigRepository.findById(id).orElse(null);
            if (config == null) {
                return TestResult.builder().ok(false).message("配置不存在").build();
            }
            baseUrl = config.getBaseUrl();
            apiKey = config.getApiKey();
            modelName = config.getModelName();
        }
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()
                || modelName == null || modelName.isBlank()) {
            return TestResult.builder().ok(false).message("缺少 baseUrl / apiKey / modelName").build();
        }

        long start = System.currentTimeMillis();
        try {
            // 规范化为完整 URL（含版本前缀）后再交给 buildChatModel，后者只拼 /chat/completions（不补 /v1）
            String normalized = AiHttpClientFactory.normalizeForStorage(baseUrl, false);
            AiModelEndpoint endpoint = new AiModelEndpoint(normalized, apiKey, modelName, 0.0);
            ChatModel chatModel = aiModelRegistry.buildChatModel(endpoint, TEST_TIMEOUT, TEST_TIMEOUT);
            chatModel.call(new Prompt("ping"));
            long latency = System.currentTimeMillis() - start;

            if (config != null) {
                config.setLastTestAt(LocalDateTime.now());
                config.setLastTestOk(true);
                aiModelConfigRepository.save(config);
            }
            return TestResult.builder().ok(true).latencyMs(latency).message("连接成功").build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("模型连接测试失败: model={},baseUrl={}, err=", modelName, baseUrl, e);
            if (config != null) {
                config.setLastTestAt(LocalDateTime.now());
                config.setLastTestOk(false);
                aiModelConfigRepository.save(config);
            }
            return TestResult.builder().ok(false).latencyMs(latency)
                    .message("连接失败：" + rootMessage(e)).build();
        }
    }

    // ========== 内部工具 ==========

    /**
     * 提取异常根因消息，避免把整条栈暴露给前端
     */
    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}

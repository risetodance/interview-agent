package interview.guide.modules.aimodel.service;

import interview.guide.modules.aimodel.model.ProbeRequest;
import interview.guide.modules.aimodel.model.ProbeResult;
import interview.guide.modules.aimodel.repository.AiModelConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * AiModelProbeService 真实连通性集成测试
 *
 * <p>用环境变量里的真实 API Key 打 /v1/models，验证 key 与 base URL 在当前环境下真的能拉到模型。
 * 仅当环境变量 MINIMAX_API_KEY 存在时才运行（@EnabledIfEnvironmentVariable），
 * 普通环境/CI 下自动跳过，不破坏 ./gradlew test 的全绿。
 *
 * <p>运行方式（需先在 shell 里 export 变量，.env 不会被 gradle 自动加载）：
 * <pre>
 * set -a && source .env && set +a
 * ./gradlew :app:test --tests "interview.guide.modules.aimodel.service.AiModelProbeServiceIntegrationTest" --info
 * </pre>
 *
 * <p>注意：此测试会真实调用 MiniMax API 的 /v1/models（列表接口，不消耗 token）。
 */
@DisplayName("AI 模型探针服务 - 真实连通性测试")
@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = "sk-.+")
class AiModelProbeServiceIntegrationTest {

    private AiModelProbeService probeService;

    @BeforeEach
    void setUp() {
        // probe(id=null) 路径只用 req 里的 baseUrl+apiKey，不碰 registry/repository；
        // 给空 mock 避免将来 probe 逻辑用到这两个协作者时 NPE。
        probeService = new AiModelProbeService(
                mock(AiModelRegistry.class),
                mock(AiModelConfigRepository.class));
    }

    @Test
    @DisplayName("用环境变量 API Key 能拉到 MiniMax 可用模型列表")
    void probeWithRealApiKeyReturnsModels() {
        String apiKey = System.getenv("MINIMAX_API_KEY");
        String host = System.getenv("MINIMAX_API_HOST");
        String baseUrl = (host != null && !host.isBlank()) ? host : "https://api.minimaxi.com/";

        ProbeResult result = probeService.probe(ProbeRequest.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build());

        assertTrue(result.isOk(),
                "拉取应成功。失败原因：" + result.getMessage());
        assertNotNull(result.getModels(), "models 列表不应为 null");
        assertFalse(result.getModels().isEmpty(), "应至少拉取到一个模型");

        // 打印拉到的模型，方便人工核对（不作为断言，不同账号可见模型集合不同）
        System.out.println("==== 拉取到 " + result.getModels().size() + " 个模型 ====");
        result.getModels().forEach(m ->
                System.out.println("  - " + m.getId() + (m.getName() != null ? "  (" + m.getName() + ")" : "")));
    }

    @Test
    @DisplayName("错误的 API Key 返回 ok=false 且不抛异常")
    void probeWithInvalidApiKeyReturnsError() {
        ProbeResult result = probeService.probe(ProbeRequest.builder()
                .baseUrl("https://api.minimaxi.com/")
                .apiKey("sk-invalid-key-for-test-only-xxxxx")
                .build());

        assertFalse(result.isOk(), "错误 key 不应拉取成功");
        assertNotNull(result.getMessage(), "应返回失败原因");
        System.out.println("==== 错误 key 的预期失败信息：" + result.getMessage() + " ====");
    }

    @Test
    @DisplayName("缺少 baseUrl / apiKey 时返回友好错误（不抛异常）")
    void probeWithMissingParamsReturnsError() {
        ProbeResult result = probeService.probe(ProbeRequest.builder().build());

        assertFalse(result.isOk(), "空请求不应成功");
        assertNotNull(result.getMessage(), "应返回失败原因");
    }
}

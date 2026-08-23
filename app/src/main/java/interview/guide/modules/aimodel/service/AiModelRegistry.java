package interview.guide.modules.aimodel.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.aimodel.enums.AiModelConfigType;
import interview.guide.modules.aimodel.model.AiModelActiveRoleEntity;
import interview.guide.modules.aimodel.model.AiModelConfigEntity;
import interview.guide.modules.aimodel.model.AiModelEndpoint;
import interview.guide.modules.aimodel.repository.AiModelActiveRoleRepository;
import interview.guide.modules.aimodel.repository.AiModelConfigRepository;
import interview.guide.modules.aimodel.util.AiHttpClientFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Optional;

/**
 * AI 模型注册中心
 * <p>核心职责（角色指派模型）：
 * <ol>
 *   <li>启动时（{@link PostConstruct}）从 ai_model_active_role 读各角色槽位指派的 configId，
 *       构建底层 OpenAiChatModel。CHAT 槽位为空 → 仅 warn 不阻断启动，业务调用 chat 时抛
 *       {@link ErrorCode#AI_MODEL_NOT_CONFIGURED}（全新部署可先拉起，再后台指派主模型）。</li>
 *   <li>包成 {@link ChatClient.Builder} bean 暴露给容器——业务代码 10+ 注入点零改动，透明命中。</li>
 *   <li>提供 {@link #reload(AiModelConfigType)} 热替换：指派变更后重建底层 ChatModel 并热生效。</li>
 *   <li>提供 {@link #getActiveConfigId(AiModelConfigType)}：供 service / DTO 查询当前指派。</li>
 * </ol>
 *
 * <p><b>热替换并发模型</b>：通过 {@link DelegatingChatModel} 单例 + {@code volatile} delegate 引用实现。
 * 所有业务代码在构造期 {@code .build()} 出的 ChatClient 内部持有该单例；reload 时只换单例的
 * delegate 引用，<b>全局所有 ChatClient 瞬间生效</b>（飞行中的请求用旧实例跑完，新请求走新模型），
 * HybridSearchService 的双 client 同样覆盖。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiModelRegistry {

    private final AiModelConfigRepository aiModelConfigRepository;
    private final AiModelActiveRoleRepository aiModelActiveRoleRepository;

    /** HTTP 超时（connect + read），与原 yml spring.ai.openai.chat 的 2 分钟超时对齐，防止请求无限挂起 */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(120);

    /** 主模型委托单例（所有主 ChatClient 共享，热替换时换 delegate） */
    private final DelegatingChatModel mainDelegating = new DelegatingChatModel();
    /** 小模型委托单例（SMALL_CHAT 槽位为空 → delegate 指向主，实现退化） */
    private final DelegatingChatModel smallDelegating = new DelegatingChatModel();

    /**
     * 启动时加载主模型与小模型；CHAT 槽位为空 → fail-fast。
     */
    @PostConstruct
    public void init() {
        reload(AiModelConfigType.CHAT);
        reload(AiModelConfigType.SMALL_CHAT);
        log.info("AI 模型注册中心初始化完成: 主模型=[{}], 小模型=[{}]",
                describeActive(AiModelConfigType.CHAT),
                describeActive(AiModelConfigType.SMALL_CHAT));
    }

    /**
     * 重建指定角色槽位的底层 ChatModel 并热替换。
     * <p>槽位指派从 ai_model_active_role 读取：
     * <ul>
     *   <li>CHAT：configId 为空 → 仅 warn 返回，delegate 保持 null，业务调用时抛 AI_MODEL_NOT_CONFIGURED。</li>
     *   <li>SMALL_CHAT：configId 为空 → 退化指向当前主模型 delegate（保留禁用语义）。</li>
     *   <li>configId 非空 → 从 ai_model_config 取凭证 → buildChatModel。</li>
     * </ul>
     *
     * @param role 角色槽位
     */
    public void reload(AiModelConfigType role) {
        Long configId = getActiveConfigId(role);
        switch (role) {
            case CHAT -> {
                if (configId == null) {
                    // CHAT 槽位为空仅警告，不阻断启动：全新部署尚未指派主模型也能正常拉起，
                    // 业务侧真正调用 chat 时由 DelegatingChatModel 抛 AI_MODEL_NOT_CONFIGURED。
                    log.warn("AI 模型配置缺失：CHAT 槽位未指派，请在管理后台指派主模型");
                    return;
                }
                AiModelConfigEntity chat = aiModelConfigRepository.findById(configId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.AI_MODEL_CONFIG_EMPTY,
                                "AI 模型配置缺失：CHAT 角色指派的 config#" + configId + " 已不存在"));
                mainDelegating.setDelegate(buildChatModel(toEndpoint(chat)));
                // 主模型重建后，若小模型处于退化态（槽位空），同步把小 delegate 指向新主模型
                refreshSmallDelegateIfDegraded();
            }
            case SMALL_CHAT -> {
                if (configId == null) {
                    // 槽位为空 = 禁用，退化到当前主模型
                    smallDelegating.setDelegate(mainDelegating.getDelegate());
                    log.info("小模型槽位未指派，退化使用主模型");
                    return;
                }
                Optional<AiModelConfigEntity> smallOpt = aiModelConfigRepository.findById(configId);
                if (smallOpt.isPresent()) {
                    smallDelegating.setDelegate(buildChatModel(toEndpoint(smallOpt.get())));
                } else {
                    // 槽位指向的 config 不存在（理论上 FK ON DELETE SET NULL 已把槽位置空，兜底退化）
                    smallDelegating.setDelegate(mainDelegating.getDelegate());
                    log.warn("小模型槽位指向的 config#{} 不存在，退化使用主模型", configId);
                }
            }
        }
    }

    /**
     * 查询某角色当前指派的 configId（供 service / DTO 渲染列表）。
     *
     * @param role 角色槽位
     * @return 指派的 configId，槽位不存在或未指派时返回 null
     */
    public Long getActiveConfigId(AiModelConfigType role) {
        return aiModelActiveRoleRepository.findByRole(role)
                .map(AiModelActiveRoleEntity::getConfigId)
                .orElse(null);
    }

    /**
     * 获取指定角色当前激活的底层 ChatModel（供 probe / test 连接用）。
     */
    public ChatModel getActiveChatModel(AiModelConfigType configType) {
        return configType == AiModelConfigType.SMALL_CHAT
                ? smallDelegating.getDelegate()
                : mainDelegating.getDelegate();
    }

    /**
     * 主模型 reload 后，若小模型当前为退化态（SMALL_CHAT 槽位空），同步更新其 delegate 到新主模型。
     */
    private void refreshSmallDelegateIfDegraded() {
        if (getActiveConfigId(AiModelConfigType.SMALL_CHAT) == null) {
            smallDelegating.setDelegate(mainDelegating.getDelegate());
        }
    }

    /**
     * 根据端点信息构建一个全新的 OpenAiChatModel（Registry 加载与 probe / test 临时调用共用此入口）。
     * <p>使用默认 HTTP 超时（{@link #HTTP_TIMEOUT}，与原 yml 的 2 分钟超时对齐）。
     *
     * @param endpoint 模型端点（含明文 apiKey）
     * @return OpenAiChatModel 实例
     */
    public ChatModel buildChatModel(AiModelEndpoint endpoint) {
        return buildChatModel(endpoint, HTTP_TIMEOUT, HTTP_TIMEOUT);
    }

    /**
     * 根据端点信息构建一个全新的 OpenAiChatModel，使用自定义超时（probe / test 等快速反馈场景）。
     *
     * @param endpoint       模型端点（含明文 apiKey）
     * @param connectTimeout 连接建立超时
     * @param readTimeout    响应读取超时
     * @return OpenAiChatModel 实例
     */
    public ChatModel buildChatModel(AiModelEndpoint endpoint, Duration connectTimeout, Duration readTimeout) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(AiHttpClientFactory.normalizeBaseUrl(endpoint.baseUrl()))
                .apiKey(endpoint.apiKey())
                // 入库 baseUrl 已含版本前缀，这里只拼端点后缀，不再依赖 OpenAiApi 默认的 /v1（避免 /v4/v1/... 404）
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .restClientBuilder(AiHttpClientFactory.restClientBuilder(connectTimeout, readTimeout))
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(endpoint.modelName())
                .temperature(endpoint.temperature())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * 暴露主模型 {@link ChatClient.Builder}（默认 qualifier，{@code @Primary} 覆盖自动配置）。
     * <p>业务代码注入的 ChatClient.Builder 自动命中此 bean，.build() 得到持有 mainDelegating 单例的 ChatClient。
     */
    @Bean
    @Primary
    public ChatClient.Builder chatClientBuilder() {
        return ChatClient.builder(mainDelegating);
    }

    /**
     * 暴露小模型 {@link ChatClient.Builder}，qualifier = {@code smallModelChatClientBuilder}。
     * <p>HybridSearchService 通过此 qualifier 注入，取代原 yml 自建小模型逻辑。
     */
    @Bean(name = "smallModelChatClientBuilder")
    public ChatClient.Builder smallModelChatClientBuilder() {
        return ChatClient.builder(smallDelegating);
    }

    // ========== 内部工具 ==========

    /** Entity → 构建端点 record（仅取构建 ChatModel 所需字段，避免持久化对象泄漏为传输契约） */
    private static AiModelEndpoint toEndpoint(AiModelConfigEntity entity) {
        return new AiModelEndpoint(entity.getBaseUrl(), entity.getApiKey(),
                entity.getModelName(), entity.getTemperature());
    }

    private String describeActive(AiModelConfigType configType) {
        ChatModel active = getActiveChatModel(configType);
        return active == null ? "<未加载>" : active.getDefaultOptions().getModel();
    }

    /**
     * 委托型 ChatModel：内部 {@code volatile} 持有真实 ChatModel，reload 时换引用即可全局热生效。
     * <p>仅 {@code call(Prompt)} / {@code stream(Prompt)} / {@code getDefaultOptions()} 显式委托；
     * 其余 default 方法（如 {@code call(String)}、{@code call(Message...)}）内部调用 {@code call(Prompt)}，
     * 故自动委托到最新 delegate。
     */
    public static class DelegatingChatModel implements ChatModel {

        private volatile ChatModel delegate;

        void setDelegate(ChatModel delegate) {
            this.delegate = delegate;
        }

        ChatModel getDelegate() {
            return delegate;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            ensureConfigured();
            return delegate.call(alignPromptOptions(prompt));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            ensureConfigured();
            return delegate.stream(alignPromptOptions(prompt));
        }

        /**
         * 修正 Prompt 里的 options，确保 model 名等关键字段用当前 delegate（最新模型）的值。
         * <p>背景：ChatClient 调用链会在 Prompt 里注入 options（来自某处缓存），
         * 切换模型后 promptOptions 里的 model 名是旧的，会覆盖 delegate 的 defaultOptions，
         * 导致「URL 切了但模型名没切」（MiniMax URL + 旧 glm-5.2 模型名）。
         * 这里在委托前用 delegate 的 model 名强制覆盖，保证一致性。
         */
        private Prompt alignPromptOptions(Prompt prompt) {
            org.springframework.ai.chat.prompt.ChatOptions delegateOpts = delegate.getDefaultOptions();
            org.springframework.ai.chat.prompt.ChatOptions promptOpts = prompt.getOptions();
            if (delegateOpts instanceof org.springframework.ai.openai.OpenAiChatOptions dOpts
                    && promptOpts instanceof org.springframework.ai.openai.OpenAiChatOptions pOpts) {
                // 修复热切换后 model 名不同步：ChatClient 调用链会在 Prompt 里注入缓存的旧 options，
                // 切换模型后 promptOptions.model 是旧的，会覆盖 delegate 的 defaultOptions，
                // 导致「URL 切了但模型名没切」（MiniMax URL + 旧 glm-5.2 模型名）。
                // 这里用 delegate（当前激活模型）的 model 名强制覆盖 promptOptions，保证一致性。
                String delegateModel = dOpts.getModel();
                String promptModel = pOpts.getModel();
                Double delegateTemp = dOpts.getTemperature();
                Double promptTemp = pOpts.getTemperature();
                boolean modelChanged = promptModel == null || !promptModel.equals(delegateModel);
                boolean tempChanged = promptTemp == null || !promptTemp.equals(delegateTemp);
                log.info("LLM 调用: model={} (prompt={}{}), temperature={} (prompt={}{})",
                        delegateModel, promptModel, modelChanged ? " [已对齐]" : "",
                        delegateTemp, promptTemp, tempChanged ? " [已对齐]" : "");
                pOpts.setModel(delegateModel);
                pOpts.setTemperature(delegateTemp);
            }
            return prompt;
        }

       @Override
       public ChatOptions getDefaultOptions() {
            // 启动期宽容：ChatClient.builder(...) 构造 DefaultChatClientRequestSpec 时会立即调用本方法，
            // 若此时数据库尚无模型配置（新环境首次部署、后台还没来得及配），抛异常会导致整个应用启动失败、
            // 管理后台无法访问，形成"起不来→配不了"死锁。故此处返回占位空选项让应用正常启动；
            // 真正的强校验保留在 call/stream 调用期（ensureConfigured），未配置时业务接口返回明确提示。
            ChatModel current = delegate;
            return current != null ? current.getDefaultOptions() : OpenAiChatOptions.builder().build();
        }

        /**
         * 槽位未指派主模型时抛语义清晰的业务异常，前端可据此提示「暂无可用的 AI 模型」。
         */
        private void ensureConfigured() {
            if (delegate == null) {
                throw new BusinessException(ErrorCode.AI_MODEL_NOT_CONFIGURED,
                        "AI 模型未配置，请联系管理员在后台配置");
            }
        }
    }
}

package interview.guide.modules.aimodel.util;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * AI 模型 HTTP 客户端构造工具
 * <p>抽取 {@code AiModelRegistry} 与 {@code AiModelProbeService} 共用的三段逻辑，消除重复并统一超时与 baseUrl 语义：
 * <ol>
 *   <li>{@link #normalizeBaseUrl(String)}：调用前规范化，保证 base-url 以 {@code /} 结尾，便于与 {@code /chat/completions}
 *       等端点拼接（不补版本前缀——按约定入库 baseUrl 已是完整 URL）。</li>
 *   <li>{@link #normalizeForStorage(String, boolean)}：保存前规范化，把用户填的 baseUrl 规整为完整 URL（含版本前缀 /v1、/v4），
 *       由 AiModelConfigService 在 create/update 时调用。</li>
 *   <li>{@link #restClientBuilder(Duration, Duration)}：构造带 <b>connect + read</b> 双超时的 {@link RestClient.Builder}。
 *       connect 超时只能由底层 {@link HttpClient} 设定，read 超时由 {@link JdkClientHttpRequestFactory} 设定——
 *       ProbeService 原实现只设了 readTimeout 漏设 connectTimeout，对端不响应 connect 时会无限挂起，此处统一修复。</li>
 * </ol>
 *
 * <p><b>baseUrl 规范化约定</b>：入库 base_url 永远是完整 URL（含版本前缀，如 {@code /v1}、{@code /v4}）；
 * 调用方（Registry / Probe）只拼端点后缀（{@code /chat/completions}、{@code /models}、{@code /embeddings}），不再补 {@code /v1}。
 * 兼容 MiniMax（{@code /v1}）与智谱 GLM（{@code /v4}），避免 {@code /v4/v1/chat/completions} 404。
 */
public final class AiHttpClientFactory {

    private AiHttpClientFactory() {
    }

    /**
     * 调用前规范化：保证 baseUrl 以 {@code /} 结尾，便于拼接 {@code /chat/completions} 等端点。
     * <p>不补版本前缀——按约定入库 baseUrl 已是完整 URL（含 /v1 或 /v4）。
     */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /**
     * 保存前规范化 baseUrl 为完整 URL（含版本前缀）。
     * <p>规则：先去尾斜杠；
     * <ul>
     *   <li>{@code useFullUrl=true}：用户填了完整 URL，原样返回（仅去尾斜杠）。</li>
     *   <li>{@code useFullUrl=false}：URL 已以 {@code /v数字} 结尾（如 /v1、/v4）则不补；否则补 {@code /v1}。</li>
     * </ul>
     *
     * @param rawUrl     用户填的原始 URL
     * @param useFullUrl true=用户填完整 URL；false=域名根（后端补 /v1）
     * @return 规范化后的完整 URL（不含尾斜杠）；入参空则原样返回
     */
    public static String normalizeForStorage(String rawUrl, boolean useFullUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }
        String url = rawUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (useFullUrl) {
            return url;
        }
        // 已含版本前缀 /v数字（如 /v1 /v4）则不补
        if (url.matches(".*(/v\\d+)$")) {
            return url;
        }
        return url + "/v1";
    }

    /**
     * 构造带 connect + read 双超时的 {@link RestClient.Builder}。
     *
     * @param connectTimeout 连接建立超时（由底层 HttpClient 承载）
     * @param readTimeout    响应读取超时（由 requestFactory 承载）
     */
    public static RestClient.Builder restClientBuilder(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}

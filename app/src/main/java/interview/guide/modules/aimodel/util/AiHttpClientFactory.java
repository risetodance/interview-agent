package interview.guide.modules.aimodel.util;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
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
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(SsrfGuardInterceptor.INSTANCE);
    }

    // ========== SSRF 防护 ==========

    /**
     * SSRF 防护拦截器：请求发出前解析目标 host 的所有 IP 地址，
     * 任一落入禁止段即拒绝连接。
     * <p>允许：公网地址 + 回环地址（本地开发连 ollama / lmstudio）。
     * <p>拒绝：RFC 1918 私网（10/8、172.16/12、192.168/16）、链路本地（169.254/16，
     * 含云元数据 169.254.169.254）、IPv6 ULA（fc00::/7）、IPv6 链路本地（fe80::/10）、
     * 任意本地（0.0.0.0）。
     * <p>挂在 restClientBuilder 上，ProbeService（probe / test）与 Registry
     * （buildChatModel → OpenAiApi）的所有出网点零侵入覆盖。
     */
    private static final class SsrfGuardInterceptor implements ClientHttpRequestInterceptor {
        private static final SsrfGuardInterceptor INSTANCE = new SsrfGuardInterceptor();

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            URI uri = request.getURI();
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IOException("SSRF 防护：请求缺少 host (" + uri + ")");
            }
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isForbiddenAddress(addr)) {
                    throw new IOException("SSRF 防护：目标地址被拦截 (" + host
                            + " -> " + addr.getHostAddress() + ")");
                }
            }
            return execution.execute(request, body);
        }
    }

    /**
     * 判断 IP 是否落入禁止段（私网 / 链路本地 / ULA）。
     * <p>回环地址返回 false（本地开发需连 ollama / lmstudio）。
     */
    static boolean isForbiddenAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress()) {
            return true;
        }
        if (addr.isLoopbackAddress()) {
            return false;
        }
        if (addr.isLinkLocalAddress()) {
            return true;
        }
        if (addr.isSiteLocalAddress()) {
            return true;
        }
        // IPv6 ULA（fc00::/7），JDK InetAddress.isSiteLocalAddress() 不覆盖此段
        if (addr instanceof Inet6Address ipv6) {
            byte[] b = ipv6.getAddress();
            if (b.length >= 2 && (b[0] & 0xFE) == 0xFC) {
                return true;
            }
        }
        return false;
    }
}

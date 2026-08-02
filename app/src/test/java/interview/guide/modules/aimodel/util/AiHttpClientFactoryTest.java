package interview.guide.modules.aimodel.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AiHttpClientFactory 规范化逻辑单元测试
 *
 * <p>覆盖 baseUrl 两种规范化：
 * <ul>
 *   <li>{@link AiHttpClientFactory#normalizeForStorage(String, boolean)}：保存前规范化为完整 URL（含版本前缀）。</li>
 *   <li>{@link AiHttpClientFactory#normalizeBaseUrl(String)}：调用前保证以 / 结尾，便于拼端点后缀。</li>
 * </ul>
 */
@DisplayName("AI HTTP 客户端 - baseUrl 规范化")
class AiHttpClientFactoryTest {

    // ========== normalizeForStorage ==========

    @Nested
    @DisplayName("保存前规范化 normalizeForStorage")
    class NormalizeForStorageTests {

        @Test
        @DisplayName("useFullUrl=false 域名根（带尾斜杠）：补 /v1")
        void domainRootWithSlash_appendsV1() {
            assertEquals("https://api.minimaxi.com/v1",
                    AiHttpClientFactory.normalizeForStorage("https://api.minimaxi.com/", false));
        }

        @Test
        @DisplayName("useFullUrl=false 域名根（无尾斜杠）：补 /v1")
        void domainRootWithoutSlash_appendsV1() {
            assertEquals("https://api.minimaxi.com/v1",
                    AiHttpClientFactory.normalizeForStorage("https://api.minimaxi.com", false));
        }

        @Test
        @DisplayName("useFullUrl=false 已含 /v4（智谱）：不补")
        void alreadyHasV4_notAppended() {
            assertEquals("https://open.bigmodel.cn/api/paas/v4",
                    AiHttpClientFactory.normalizeForStorage("https://open.bigmodel.cn/api/paas/v4", false));
        }

        @Test
        @DisplayName("useFullUrl=false 已含 /v4 带尾斜杠：去尾斜杠、不补")
        void alreadyHasV4WithSlash_trailingSlashStripped() {
            assertEquals("https://open.bigmodel.cn/api/paas/v4",
                    AiHttpClientFactory.normalizeForStorage("https://open.bigmodel.cn/api/paas/v4/", false));
        }

        @Test
        @DisplayName("useFullUrl=true 完整 URL：原样返回（仅去尾斜杠，不补 /v1）")
        void useFullUrlTrue_returnedAsIs() {
            assertEquals("https://open.bigmodel.cn/api/paas/v4",
                    AiHttpClientFactory.normalizeForStorage("https://open.bigmodel.cn/api/paas/v4", true));
            assertEquals("https://api.minimaxi.com",
                    AiHttpClientFactory.normalizeForStorage("https://api.minimaxi.com/", true));
        }

        @Test
        @DisplayName("前后空白：trim 后规范化")
        void surroundingWhitespace_trimmed() {
            assertEquals("https://api.minimaxi.com/v1",
                    AiHttpClientFactory.normalizeForStorage("  https://api.minimaxi.com/  ", false));
        }

        @Test
        @DisplayName("null / 空串 / 纯空白：原样返回")
        void nullOrBlank_returnedAsIs() {
            assertNull(AiHttpClientFactory.normalizeForStorage(null, false));
            assertEquals("", AiHttpClientFactory.normalizeForStorage("", false));
            assertEquals("   ", AiHttpClientFactory.normalizeForStorage("   ", false));
        }
    }

    // ========== normalizeBaseUrl ==========

    @Nested
    @DisplayName("调用前规范化 normalizeBaseUrl")
    class NormalizeBaseUrlTests {

        @Test
        @DisplayName("无尾斜杠：补 /")
        void withoutSlash_appendsSlash() {
            assertEquals("https://api.minimaxi.com/v1/",
                    AiHttpClientFactory.normalizeBaseUrl("https://api.minimaxi.com/v1"));
        }

        @Test
        @DisplayName("有尾斜杠：不变")
        void withSlash_unchanged() {
            assertEquals("https://api.minimaxi.com/v1/",
                    AiHttpClientFactory.normalizeBaseUrl("https://api.minimaxi.com/v1/"));
        }

        @Test
        @DisplayName("null / 空串：原样返回")
        void nullOrBlank_returnedAsIs() {
            assertNull(AiHttpClientFactory.normalizeBaseUrl(null));
            assertEquals("", AiHttpClientFactory.normalizeBaseUrl(""));
        }
    }
}

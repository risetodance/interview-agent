package interview.guide.modules.aimodel.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHttpClientFactoryTest {

    // ===== 禁止段（应被拦截） =====

    @ParameterizedTest
    @ValueSource(strings = {
        "10.0.0.1",        // RFC1918 10/8
        "10.255.255.255",
        "172.16.0.1",      // RFC1918 172.16/12
        "172.31.255.255",
        "192.168.1.1",     // RFC1918 192.168/16
        "169.254.169.254", // 链路本地（云元数据）
        "169.254.0.1",
        "0.0.0.0",         // 任意本地
    })
    void forbiddenPrivateOrLinkLocal(String ip) throws Exception {
        assertTrue(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName(ip)),
            () -> ip + " 应被 SSRF 防护拦截");
    }

    @Test
    void forbiddenIpv6Ula() throws Exception {
        // fc00::/7（IPv6 唯一本地地址）
        InetAddress fc = InetAddress.getByName("fc00::1");
        InetAddress fd = InetAddress.getByName("fd12:3456::1");
        assertTrue(AiHttpClientFactory.isForbiddenAddress(fc));
        assertTrue(AiHttpClientFactory.isForbiddenAddress(fd));
    }

    @Test
    void forbiddenIpv6LinkLocal() throws Exception {
        InetAddress fe = InetAddress.getByName("fe80::1");
        assertTrue(AiHttpClientFactory.isForbiddenAddress(fe));
    }

    // ===== 允许段（不应被拦截） =====

    @Test
    void loopbackAllowed() throws Exception {
        // 回环地址允许（本地开发连 ollama / lmstudio）
        assertFalse(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName("::1")));
    }

    @Test
    void publicAddressAllowed() throws Exception {
        assertFalse(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName("1.1.1.1")));
    }

    // ===== 172.16/12 边界 =====

    @Test
    void boundary172() throws Exception {
        assertTrue(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName("172.16.0.0")));
        assertTrue(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName("172.31.255.255")));
        // 172.15 / 172.32 是公网地址，不应被拦截
        assertFalse(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName("172.15.0.1")));
        assertFalse(AiHttpClientFactory.isForbiddenAddress(InetAddress.getByName("172.32.0.1")));
    }
}

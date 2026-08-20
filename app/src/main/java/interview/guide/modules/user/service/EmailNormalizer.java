package interview.guide.modules.user.service;

import java.util.Locale;

/**
 * 邮箱规范化工具（trim + 统一小写）
 * 安全关键规则：认证链路（发码/验码/登录/占用检查）与入库必须使用同一份规范化，
 * 否则大小写变体可绕过 60s 限发、造成同邮箱重复建号与验码 key 漂移。
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /**
     * null 安全：返回 null；否则 trim + 小写（Locale.ROOT 防土耳其语等默认 locale 漂移）
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

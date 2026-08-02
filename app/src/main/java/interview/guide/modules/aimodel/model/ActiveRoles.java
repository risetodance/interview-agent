package interview.guide.modules.aimodel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 当前角色指派映射
 * <p>JSON 形如 {@code {"CHAT": 1, "SMALL_CHAT": null}}：
 * <ul>
 *   <li>值 = configId：该角色当前指向的凭证 id</li>
 *   <li>值 = null：该槽位未指派（SMALL_CHAT 空 = 禁用退化主模型；CHAT 空 = 异常态，启动 fail-fast）</li>
 * </ul>
 * 用 {@code @JsonProperty} 把 JSON key 固定为 CHAT / SMALL_CHAT，Java 字段保留驼峰可读性。
 *
 * @param chat      CHAT 槽位当前指派的 configId（可空）
 * @param smallChat SMALL_CHAT 槽位当前指派的 configId（可空）
 */
public record ActiveRoles(
        @JsonProperty("CHAT") Long chat,
        @JsonProperty("SMALL_CHAT") Long smallChat
) {
}

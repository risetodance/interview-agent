package interview.guide.modules.aimodel.enums;

/**
 * AI 模型角色槽位
 * <p>角色指派重构后：语义从「配置类型」改为「角色槽位」。凭证（ai_model_config）不绑类型，
 * 本枚举用于 ai_model_active_role 的两行槽位：
 * <ul>
 *   <li>CHAT 主对话模型槽位（不可为空，否则启动 fail-fast）</li>
 *   <li>SMALL_CHAT 小模型槽位（可为空，空 = 禁用并退化使用主模型，用于 reranker 等轻量任务）</li>
 * </ul>
 */
public enum AiModelConfigType {
    CHAT,       // 主对话模型
    SMALL_CHAT  // 小模型（reranker 等轻量任务）
}

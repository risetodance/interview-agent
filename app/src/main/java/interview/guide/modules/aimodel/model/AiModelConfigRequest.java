package interview.guide.modules.aimodel.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 模型配置创建 / 更新请求体（纯凭证）
 * <p>角色指派重构后：请求不再带 configType / enabled——凭证与角色解耦，
 * 创建出的凭证默认不被任何角色引用，需在列表里通过「启用为主模型 / 启用为小模型」显式指派。
 * <p>api_key 为明文（仅在请求体内传输，落盘明文存储）。
 * 更新时 apiKey 为空字符串 / null / 脱敏占位（******）均判定为「不修改」，保留旧明文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelConfigRequest {

    @NotBlank(message = "供应商不能为空")
    private String provider;

    @NotBlank(message = "显示名称不能为空")
    private String displayName;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    /** 明文 api key；新建必填，更新时空 / 占位则不修改（校验在 service 层） */
    private String apiKey;

    @NotBlank(message = "模型名不能为空")
    private String modelName;

    private Double temperature;

    /** baseUrl 输入模式：true=用户填完整 URL（原样入库）；false=域名根，后端补 /v1。默认 false。 */
    @Builder.Default
    private Boolean useFullUrl = false;
}

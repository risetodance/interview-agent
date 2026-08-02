package interview.guide.modules.aimodel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拉取模型列表请求体（probe）
* <p>apiKey 明文，仅临时用于本次拉取，不落盘
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProbeRequest {

    /** 已存配置 id（优先）：传入后用该配置已存的 baseUrl + apiKey 拉取，避免编辑态要求重填 key */
    private Long id;

    private String baseUrl;

    private String apiKey;
}

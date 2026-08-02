package interview.guide.modules.aimodel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型连接测试请求体（test）
 * <p>二选一：传入 id 用已存配置测试（成功回写 last_test_at/ok）；或传入 baseUrl/apiKey/modelName 测试未保存配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestRequest {

    /** 已存配置 id（优先） */
    private Long id;

    /** 未保存配置的 base url */
    private String baseUrl;

    /** 未保存配置的 api key（明文） */
    private String apiKey;

    /** 未保存配置的模型名 */
    private String modelName;
}

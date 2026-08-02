package interview.guide.modules.aimodel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型连接测试结果（test）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {

    /** 是否连通 */
    private boolean ok;

    /** 延迟（毫秒） */
    private Long latencyMs;

    private String message;
}

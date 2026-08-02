package interview.guide.modules.aimodel.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 拉取可用模型列表结果（probe）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProbeResult {

    private List<ModelInfo> models;
    private boolean ok;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelInfo {
        private String id;
        private String name;
    }
}

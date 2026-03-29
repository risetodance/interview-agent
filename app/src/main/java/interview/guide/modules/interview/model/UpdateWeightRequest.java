package interview.guide.modules.interview.model;

import jakarta.validation.constraints.*;

/**
 * 更新角色权重请求
 */
public record UpdateWeightRequest(
    @NotNull(message = "权重不能为空")
    @DecimalMin(value = "0.0", message = "权重最小为0")
    @DecimalMax(value = "1.0", message = "权重最大为1.0")
    Double weight
) {}

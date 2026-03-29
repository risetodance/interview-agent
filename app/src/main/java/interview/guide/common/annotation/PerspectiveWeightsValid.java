package interview.guide.common.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 校验视角权重配置：各权重在 0-1 之间且总和为 1（允许 ±0.01 误差）
 */
@Documented
@Constraint(validatedBy = PerspectiveWeightsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PerspectiveWeightsValid {
    String message() default "视角权重配置错误：各权重值需在0-1之间，且总和需等于1（允许±0.01误差）";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

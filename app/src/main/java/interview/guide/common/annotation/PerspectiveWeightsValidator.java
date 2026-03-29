package interview.guide.common.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.Map.Entry;

/**
 * PerspectiveWeightsValid 校验器实现
 */
public class PerspectiveWeightsValidator implements ConstraintValidator<PerspectiveWeightsValid, Map<Long, Double>> {

    private static final double TOLERANCE = 0.01;
    private static final double MIN_WEIGHT = 0.0;
    private static final double MAX_WEIGHT = 1.0;

    @Override
    public boolean isValid(Map<Long, Double> weights, ConstraintValidatorContext context) {
        if (weights == null || weights.isEmpty()) {
            // 空值视为合法（表示使用角色默认权重）
            return true;
        }

        double sum = 0.0;
        StringBuilder errors = new StringBuilder();

        for (Entry<Long, Double> entry : weights.entrySet()) {
            Double weight = entry.getValue();
            if (weight == null) {
                buildMessage(context, "视角 " + entry.getKey() + " 的权重值不能为空");
                return false;
            }
            if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                buildMessage(context, "视角 " + entry.getKey() + " 的权重值 " + weight + " 超出范围 [0, 1]");
                return false;
            }
            sum += weight;
        }

        if (Math.abs(sum - 1.0) > TOLERANCE) {
            buildMessage(context, String.format("视角权重之和不等于1，当前总和: %.2f（允许误差 ±%.2f）", sum, TOLERANCE));
            return false;
        }

        return true;
    }

    private void buildMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}

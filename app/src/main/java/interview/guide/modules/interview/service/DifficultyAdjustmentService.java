package interview.guide.modules.interview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 难度调整服务
 * 根据用户答题表现自动调整面试难度
 */
@Slf4j
@Service
public class DifficultyAdjustmentService {

    /**
     * 难度等级
     */
    public enum Difficulty {
        BASIC("BASIC", "基础"),
        ADVANCED("ADVANCED", "进阶"),
        EXPERT("EXPERT", "专家");

        private final String code;
        private final String desc;

        Difficulty(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static Difficulty fromCode(String code) {
            for (Difficulty d : values()) {
                if (d.code.equalsIgnoreCase(code)) {
                    return d;
                }
            }
            return BASIC;
        }
    }

    /**
     * 根据得分调整难度
     * - 得分 >= 90: 升级难度 (BASIC->ADVANCED->EXPERT)
     * - 得分 60-89: 保持当前难度
     * - 得分 < 60: EXPERT 降到 ADVANCED，其他保持
     *
     * @param currentDifficulty 当前难度
     * @param score             得分 (0-100)
     * @return 调整后的难度
     */
    public String adjustDifficulty(String currentDifficulty, int score) {
        Difficulty current = Difficulty.fromCode(currentDifficulty);

        if (score >= 90) {
            // 升级难度
            return upgradeDifficulty(current).getCode();
        } else if (score >= 60) {
            // 保持当前难度
            return current.getCode();
        } else {
            // 得分 < 60，EXPERT 降到 ADVANCED
            return downgradeDifficulty(current).getCode();
        }
    }

    /**
     * 升级难度
     */
    private Difficulty upgradeDifficulty(Difficulty current) {
        return switch (current) {
            case BASIC -> Difficulty.ADVANCED;
            case ADVANCED -> Difficulty.EXPERT;
            case EXPERT -> Difficulty.EXPERT;
        };
    }

    /**
     * 降级难度
     */
    private Difficulty downgradeDifficulty(Difficulty current) {
        return switch (current) {
            case BASIC -> Difficulty.BASIC;
            case ADVANCED -> Difficulty.BASIC;
            case EXPERT -> Difficulty.ADVANCED;
        };
    }

    /**
     * 获取难度描述
     */
    public String getDifficultyDesc(String difficulty) {
        return Difficulty.fromCode(difficulty).getDesc();
    }

    /**
     * 判断是否达到了升级条件
     */
    public boolean shouldUpgrade(String currentDifficulty, int score) {
        return score >= 90;
    }

    /**
     * 判断是否需要降级
     */
    public boolean shouldDowngrade(String currentDifficulty, int score) {
        if (score >= 60) {
            return false;
        }
        return Difficulty.fromCode(currentDifficulty) == Difficulty.EXPERT;
    }
}

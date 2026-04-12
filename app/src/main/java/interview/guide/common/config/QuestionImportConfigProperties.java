package interview.guide.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 题目导入配置属性
 */
@Component
@ConfigurationProperties(prefix = "app.question.import")
public class QuestionImportConfigProperties {

    /**
     * 单次导入最大题目数量
     */
    private int maxCount = 1000;

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }
}

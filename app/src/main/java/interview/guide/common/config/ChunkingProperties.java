package interview.guide.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库分块配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.knowledgebase.chunking")
public class ChunkingProperties {

    /**
     * Parent 段落大小限制（Token）
     */
    private int parentMaxTokens = 1200;

    /**
     * Child 句子大小限制（Token）
     */
    private int childMaxTokens = 300;

    /**
     * 检索时返回 Top-K Child
     */
    private int retrievalTopK = 10;

    /**
     * 检索时返回 Top-N Parent
     */
    private int retrievalTopN = 5;
}
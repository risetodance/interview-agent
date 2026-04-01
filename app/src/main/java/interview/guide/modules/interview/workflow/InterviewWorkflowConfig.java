package interview.guide.modules.interview.workflow;

import com.alibaba.cloud.ai.graph.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 面试工作流 Graph 配置
 */
@Configuration
public class InterviewWorkflowConfig {

    @Bean
    public StateGraph interviewWorkflowGraph() {
        // Graph 定义将在 WorkflowExecutor 类中构建
        return null;
    }
}

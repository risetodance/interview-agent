package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单题评估服务
 * 用于实时评估用户提交的答案
 */
@Slf4j
@Service
public class SingleAnswerEvaluationService {

    private final ChatClient chatClient;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<SingleEvaluationDTO> outputConverter;
    private final ToolCallbackProvider toolCallbackProvider;


    private record SingleEvaluationDTO(
        int score,
        String feedback,
        String referenceAnswer,
        List<String> keyPoints,
        boolean adjustDifficulty,
        DifficultyAdjustmentService.Difficulty adjustedDifficulty,
        String adjustReason
    ) {}

    public SingleAnswerEvaluationService(
            @Autowired(required = false) ToolCallbackProvider toolCallbackProvider,
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/interview-evaluation-single-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/interview-evaluation-single-user.st") Resource userPromptResource) throws IOException {
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(SingleEvaluationDTO.class);
    }

    /**
     * 获取web_search工具
     */
    private ToolCallback getWebSearchCallback() {
        if (toolCallbackProvider == null) {
            return null;
        }
        for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
            if ("web_search".equals(callback.getToolDefinition().name())) {
                return callback;
            }
        }
        return null;
    }

    /**
     * 评估单个答案（不带视角信息）
     */
    public EvaluationResult evaluateAnswer(String question, String category, String difficulty,
                                          String userAnswer, String resumeText, String referenceContext) {
        return evaluateAnswer(question, category, difficulty, userAnswer, resumeText, referenceContext, null, null);
    }

    /**
     * 评估单个答案
     *
     * @param question        问题内容
     * @param category       问题分类
     * @param difficulty     难度等级
     * @param userAnswer     用户答案
     * @param resumeText     简历文本
     * @param referenceContext 参考上下文（可选）
     * @param perspectiveName 面试官视角名称（可选）
     * @param perspectivePrompt 面试官视角的评估prompt（可选）
     * @return 评估结果，包含得分、反馈、参考答案等
     */
    public EvaluationResult evaluateAnswer(String question, String category, String difficulty,
                                          String userAnswer, String resumeText, String referenceContext,
                                          String perspectiveName, String perspectivePrompt) {
        log.info("开始评估答案: category={}, difficulty={}, perspective={}", category, difficulty, perspectiveName);

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            variables.put("category", category);
            variables.put("difficulty", difficulty);
            variables.put("role", perspectivePrompt);
            variables.put("userAnswer", userAnswer != null ? userAnswer : "");
            variables.put("resumeText", resumeText != null ? resumeText : "无简历信息");

            if (referenceContext != null && !referenceContext.isBlank()) {
                variables.put("referenceContext", referenceContext);
                log.info("<UNK>: referenceContext={}", referenceContext);
            } else {
                variables.put("referenceContext", "无");
            }

            String userPrompt = userPromptTemplate.render(variables);
            String systemPrompt = systemPromptTemplate.render(variables);

            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();

            // 获取web_search工具
            ToolCallback webSearchCallback = getWebSearchCallback();

            SingleEvaluationDTO dto = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPromptWithFormat,
                    userPrompt,
                    outputConverter,
                    ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "答案评估失败：",
                    "单题评估",
                    log,
                    webSearchCallback
            );

            if (dto == null) {
                log.warn("AI评估结果为空，使用默认评估");
                return createDefaultResult();
            }

            log.info("答案评估完成: score={}, perspective={}", dto.score(), perspectiveName);

            return new EvaluationResult(
                    dto.score(),
                    dto.feedback(),
                    dto.referenceAnswer(),
                    dto.keyPoints(),
                    dto.adjustDifficulty(),
                    dto.adjustedDifficulty(),
                    dto.adjustReason()
            );

        } catch (Exception e) {
            log.error("答案评估失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "答案评估失败：" + e.getMessage());
        }
    }

    @NotNull
    private static String getSystemPrompt(String perspectivePrompt, String baseSystemPrompt) {
        String systemPrompt;
        if (perspectivePrompt != null && !perspectivePrompt.isBlank()) {
            // 用视角prompt替换占位符
            systemPrompt = baseSystemPrompt.replace("{{ROLE}}", perspectivePrompt);
        } else {
            // 使用默认角色
            systemPrompt = baseSystemPrompt.replace("{{ROLE}}",
                    "你是一位拥有 10 年以上经验的资深 Java 后端技术专家及大厂（如阿里、腾讯、字节）面试官。你具备以下核心能力：\n" +
                    "- **技术洞察力**：能通过候选人回答识别其技术边界与知识盲区\n" +
                    "- **深度评估力**：精通底层原理（JVM、并发模型、分布式一致性），能区分\"背书式回答\"与\"真正理解\"\n" +
                    "- **实战判断力**：能评估候选人将技术应用于复杂业务场景的能力");
        }
        return systemPrompt;
    }

    /**
     * 创建默认评估结果
     */
    private EvaluationResult createDefaultResult() {
        return new EvaluationResult(
                50,
                "评估服务暂时不可用，请稍后重试。",
                "暂无参考答案",
                List.of(),
                false,
                null,
                null
        );
    }

    /**
     * 评估结果
     */
    public record EvaluationResult(
            int score,
            String feedback,
            String referenceAnswer,
            List<String> keyPoints,
            boolean adjustDifficulty,
            DifficultyAdjustmentService.Difficulty adjustedDifficulty,
            String adjustReason
    ) {
        public String getKeyPointsJson() {
            try {
                // 使用新实例，避免静态引用问题
                return new ObjectMapper().writeValueAsString(keyPoints);
            } catch (Exception e) {
                return "[]";
            }
        }
    }
}

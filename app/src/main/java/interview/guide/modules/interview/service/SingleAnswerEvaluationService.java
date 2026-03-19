package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
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
    private final ObjectMapper objectMapper;

    private record SingleEvaluationDTO(
        int score,
        String feedback,
        String referenceAnswer,
        List<String> keyPoints
    ) {}

    public SingleAnswerEvaluationService(
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/interview-evaluation-single-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/interview-evaluation-single-user.st") Resource userPromptResource,
            ObjectMapper objectMapper) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(SingleEvaluationDTO.class);
        this.objectMapper = objectMapper;
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
     * @return 评估结果，包含得分、反馈、参考答案等
     */
    public EvaluationResult evaluateAnswer(String question, String category, String difficulty,
                                          String userAnswer, String resumeText, String referenceContext) {
        log.info("开始评估答案: category={}, difficulty={}", category, difficulty);

        try {
            String systemPrompt = systemPromptTemplate.render();

            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            variables.put("category", category);
            variables.put("difficulty", difficulty);
            variables.put("userAnswer", userAnswer != null ? userAnswer : "");
            variables.put("resumeText", resumeText != null ? resumeText : "无简历信息");

            if (referenceContext != null && !referenceContext.isBlank()) {
                variables.put("referenceContext", referenceContext);
            } else {
                variables.put("referenceContext", "无");
            }

            String userPrompt = userPromptTemplate.render(variables);

            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();

            SingleEvaluationDTO dto = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPromptWithFormat,
                    userPrompt,
                    outputConverter,
                    ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "答案评估失败：",
                    "单题评估",
                    log
            );

            if (dto == null) {
                log.warn("AI评估结果为空，使用默认评估");
                return createDefaultResult();
            }

            log.info("答案评估完成: score={}", dto.score());

            return new EvaluationResult(
                    dto.score(),
                    dto.feedback(),
                    dto.referenceAnswer(),
                    dto.keyPoints()
            );

        } catch (Exception e) {
            log.error("答案评估失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                    "答案评估失败：" + e.getMessage());
        }
    }

    /**
     * 创建默认评估结果
     */
    private EvaluationResult createDefaultResult() {
        return new EvaluationResult(
                50,
                "评估服务暂时不可用，请稍后重试。",
                "暂无参考答案",
                List.of()
        );
    }

    /**
     * 评估结果
     */
    public record EvaluationResult(
            int score,
            String feedback,
            String referenceAnswer,
            List<String> keyPoints
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

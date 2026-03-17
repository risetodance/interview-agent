package interview.guide.modules.resume.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.interview.model.ResumeAnalysisResponse.ScoreDetail;
import interview.guide.modules.interview.model.ResumeAnalysisResponse.Suggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历评分服务
 * 使用Spring AI调用LLM对简历进行评分和建议
 */
@Service
public class ResumeGradingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeGradingService.class);

    private final ChatClient chatClient;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final ObjectMapper objectMapper;

    private final BeanOutputConverter<ResumeAnalysisResponseDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;

    // 中间DTO用于接收AI响应
    private record ResumeAnalysisResponseDTO(
        int overallScore,
        ScoreDetailDTO scoreDetail,
        String summary,
        List<String> strengths,
        // 匹配的岗位列表
        List<String> matchedPositions,
        List<SuggestionDTO> suggestions
    ) {}

    private record ScoreDetailDTO(
        int contentScore,
        int structureScore,
        int skillMatchScore,
        int expressionScore,
        int projectScore
    ) {}

    private record SuggestionDTO(
        String category,
        String priority,
        String issue,
        String recommendation
    ) {}

    public ResumeGradingService(
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/resume-analysis-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/resume-analysis-user.st") Resource userPromptResource) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(ResumeAnalysisResponseDTO.class);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 分析简历并返回评分和建议
     *
     * @param resumeText 简历文本内容
     * @return 分析结果
     */
    public ResumeAnalysisResponse analyzeResume(String resumeText) {
        log.info("开始分析简历，文本长度: {} 字符", resumeText.length());

        try {
            // 加载系统提示词
            String systemPrompt = systemPromptTemplate.render();

            // 加载用户提示词并填充变量
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeText", resumeText);
            String userPrompt = userPromptTemplate.render(variables);

            // 添加格式指令到系统提示词
            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();

            // 调用AI
            ResumeAnalysisResponseDTO dto;
            try {
                dto = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPromptWithFormat,
                    userPrompt,
                    outputConverter,
                    ErrorCode.RESUME_ANALYSIS_FAILED,
                    "简历分析失败：",
                    "简历分析",
                    log
                );
                log.debug("AI响应解析成功: overallScore={}", dto.overallScore());
            } catch (Exception e) {
                log.error("简历分析AI调用失败: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "简历分析失败：" + e.getMessage());
            }

            // 转换为业务对象
            ResumeAnalysisResponse result = convertToResponse(dto, resumeText);
            log.info("简历分析完成，总分: {}", result.overallScore());

            return result;

        } catch (Exception e) {
            log.error("简历分析失败: {}", e.getMessage(), e);
            return createErrorResponse(resumeText, e.getMessage());
        }
    }



    /**
     * 转换DTO为业务对象
     */
    private ResumeAnalysisResponse convertToResponse(ResumeAnalysisResponseDTO dto, String originalText) {
        ScoreDetail scoreDetail = new ScoreDetail(
                dto.scoreDetail().contentScore(),
                dto.scoreDetail().structureScore(),
                dto.scoreDetail().skillMatchScore(),
                dto.scoreDetail().expressionScore(),
                dto.scoreDetail().projectScore()
        );

        List<Suggestion> suggestions = dto.suggestions().stream()
                .map(s -> new Suggestion(s.category(), s.priority(), s.issue(), s.recommendation()))
                .toList();

        return new ResumeAnalysisResponse(
                dto.overallScore(),
                scoreDetail,
                dto.summary(),
                dto.strengths(),
                suggestions,
                dto.matchedPositions(),
                originalText
        );
    }


    /**
     * 安全获取整数
     */
    private int safeGetInt(JsonNode node, String field) {
        try {
            return node.has(field) ? node.get(field).asInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 安全获取字符串列表
     */
    private List<String> safeGetStringList(JsonNode root, String field) {
        List<String> list = new ArrayList<>();
        try {
            if (root.has(field) && root.get(field).isArray()) {
                root.get(field).forEach(node -> {
                    try {
                        list.add(node.asText());
                    } catch (Exception e) {
                        // 忽略
                    }
                });
            }
        } catch (Exception e) {
            // 忽略
        }
        return list;
    }

    /**
     * 安全获取建议列表
     */
    private List<Suggestion> safeGetSuggestions(JsonNode root) {
        List<Suggestion> suggestions = new ArrayList<>();
        try {
            if (root.has("suggestions") && root.get("suggestions").isArray()) {
                root.get("suggestions").forEach(node -> {
                    try {
                        if (node.isObject()) {
                            suggestions.add(new Suggestion(
                                safeGetText(node, "category", "改进建议"),
                                safeGetText(node, "priority", "中"),
                                safeGetText(node, "issue", ""),
                                safeGetText(node, "recommendation", "")
                            ));
                        } else if (node.isTextual()) {
                            suggestions.add(new Suggestion("改进建议", "中", node.asText(), ""));
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                });
            }
        } catch (Exception e) {
            // 忽略
        }
        return suggestions;
    }

    /**
     * 安全获取文本
     */
    private String safeGetText(JsonNode node, String field, String defaultValue) {
        try {
            return node.has(field) ? node.get(field).asText(defaultValue) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 修复常见的JSON问题
     */
    private String fixJson(String jsonStr) {
        // 移除可能的markdown代码块标记
        jsonStr = jsonStr.replaceAll("^```json", "").replaceAll("^```", "").replaceAll("```$", "");
        // 移除多余空白
        jsonStr = jsonStr.trim();
        return jsonStr;
    }

    /**
     * 创建默认响应
     */
    private ResumeAnalysisResponse createDefaultResponse(String originalText) {
        return new ResumeAnalysisResponse(
            0,
            new ScoreDetail(0, 0, 0, 0, 0),
            "简历分析未能完成，请重新分析",
            List.of(),
            List.of(new Suggestion(
                "系统",
                "中",
                "分析未能完成",
                "请点击重新分析按钮"
            )),
            List.of(),
            originalText
        );
    }

    /**
     * 从AI响应中提取JSON部分
     */
    private String extractJson(String response) {
        // 尝试找到JSON的开始和结束
        int startIdx = response.indexOf('{');
        int endIdx = response.lastIndexOf('}');

        if (startIdx >= 0 && endIdx > startIdx) {
            return response.substring(startIdx, endIdx + 1);
        }

        // 如果找不到JSON，尝试找数组
        startIdx = response.indexOf('[');
        endIdx = response.lastIndexOf(']');
        if (startIdx >= 0 && endIdx > startIdx) {
            return "{\"data\":" + response.substring(startIdx, endIdx + 1) + "}";
        }

        // 无法提取，返回原始响应
        return "{}";
    }

    /**
     * 创建错误响应
     */
    private ResumeAnalysisResponse createErrorResponse(String originalText, String errorMessage) {
        return new ResumeAnalysisResponse(
            0,
            new ScoreDetail(0, 0, 0, 0, 0),
            "分析过程中出现错误: " + errorMessage,
            List.of(),
            List.of(new Suggestion(
                "系统",
                "高",
                "AI分析服务暂时不可用",
                "请稍后重试，或检查AI服务是否正常运行"
            )),
            List.of(),
            originalText
        );
    }
}

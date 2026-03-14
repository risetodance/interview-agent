package interview.guide.modules.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    public ResumeGradingService(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/resume-analysis-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/resume-analysis-user.st") Resource userPromptResource) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
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

            // JSON格式指令
            String jsonFormat = """
                请严格按照以下JSON格式返回，不要包含任何其他内容：
                {"overallScore": 0-100的整数, "scoreDetail": {"contentScore": 0-100, "structureScore": 0-100, "skillMatchScore": 0-100, "expressionScore": 0-100, "projectScore": 0-100}, "summary": "摘要内容", "strengths": ["优势1", "优势2"], "suggestions": [{"category": "类别", "priority": "优先级", "issue": "问题描述", "recommendation": "建议内容"}], "matchedPositions": ["岗位1", "岗位2"]}
                """;

            // 调用AI，获取原始字符串响应
            String aiResponse;
            try {
                // 构建完整的prompt
                String fullPrompt = systemPrompt + "\n\n" + jsonFormat + "\n\n" + userPrompt;
                aiResponse = chatClient.prompt(fullPrompt).call().content();
                log.debug("AI原始响应: {}", aiResponse);
            } catch (Exception e) {
                log.error("简历分析AI调用失败: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "简历分析失败：" + e.getMessage());
            }

            // 手动解析JSON响应
            ResumeAnalysisResponse result = parseAiResponse(aiResponse, resumeText);
            log.info("简历分析完成，总分: {}", result.overallScore());

            return result;

        } catch (Exception e) {
            log.error("简历分析失败: {}", e.getMessage(), e);
            return createErrorResponse(resumeText, e.getMessage());
        }
    }
    
    /**
     * 解析AI响应为业务对象
     */
    private ResumeAnalysisResponse parseAiResponse(String aiResponse, String originalText) {
        // 提取并尝试修复JSON
        String jsonStr = extractJson(aiResponse);
        JsonNode root;

        try {
            root = objectMapper.readTree(jsonStr);
        } catch (Exception e) {
            log.warn("JSON解析失败，尝试修复: {}", e.getMessage());
            // 尝试修复常见的JSON问题
            jsonStr = fixJson(jsonStr);
            try {
                root = objectMapper.readTree(jsonStr);
            } catch (Exception ex) {
                // 修复也失败，使用默认响应
                log.error("JSON修复失败，使用默认响应: {}", ex.getMessage());
                return createDefaultResponse(originalText);
            }
        }

        try {
            // 提取各项数据
            int overallScore = root.has("overallScore") ? root.get("overallScore").asInt() : 0;

            // 提取分数详情
            int contentScore = 0, structureScore = 0, skillMatchScore = 0, expressionScore = 0, projectScore = 0;
            if (root.has("scoreDetail") && root.get("scoreDetail").isObject()) {
                JsonNode sd = root.get("scoreDetail");
                contentScore = safeGetInt(sd, "contentScore");
                structureScore = safeGetInt(sd, "structureScore");
                skillMatchScore = safeGetInt(sd, "skillMatchScore");
                expressionScore = safeGetInt(sd, "expressionScore");
                projectScore = safeGetInt(sd, "projectScore");
            }
            ScoreDetail scoreDetail = new ScoreDetail(contentScore, structureScore, skillMatchScore, expressionScore, projectScore);

            // 提取summary
            String summary = root.has("summary") ? root.get("summary").asText("") : "";

            // 提取strengths
            List<String> strengths = safeGetStringList(root, "strengths");

            // 提取suggestions
            List<Suggestion> suggestions = safeGetSuggestions(root);

            // 提取matchedPositions
            List<String> matchedPositions = safeGetStringList(root, "matchedPositions");

            return new ResumeAnalysisResponse(overallScore, scoreDetail, summary, strengths, suggestions, matchedPositions, originalText);

        } catch (Exception e) {
            log.error("解析AI响应字段失败，使用默认响应: {}", e.getMessage());
            return createDefaultResponse(originalText);
        }
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

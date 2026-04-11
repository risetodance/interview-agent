package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.AnswerHistoryDTO;
import interview.guide.modules.interview.model.CurrentQuestionDTO;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * 问题生成服务
 * 实现一题一生成的逻辑，根据当前难度和知识库生成问题
 */
@Slf4j
@Service
public class QuestionGenerationService {

    private final ChatClient chatClient;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ObjectMapper objectMapper;
    private final BeanOutputConverter<SimpleQuestionDTO> outputConverter;

    public QuestionGenerationService(
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            KnowledgeBaseRepository knowledgeBaseRepository,
            ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.objectMapper = objectMapper;
        this.outputConverter = new BeanOutputConverter<>(SimpleQuestionDTO.class);
    }

    /**
     * 生成单个面试问题
     * AI根据简历和历史记录自行决定问题内容和分类
     *
     * @param session             会话实体
     * @param questionIndex       问题索引
     * @param resumeText          简历文本
     * @param history             历史答题记录
     * @param perspectiveId       视角ID（用于过滤历史和选择prompt）
     * @param perspectivePrompt   视角的评分prompt（作为系统提示词）
     * @param perspectiveName     视角名称
     * @param mergedSearchContext 混合检索结果上下文（已归一化重排序）
     * @param questionDirection   Decider输出的出题方向（如果方向匹配则参考）
     * @return 包含问题内容和参考上下文的 DTO
     */
    public CurrentQuestionDTO generateSingleQuestion(InterviewSessionEntity session, int questionIndex,
                                                     String resumeText, List<AnswerHistoryDTO> history,
                                                     Long perspectiveId, String perspectivePrompt,
                                                     String perspectiveName,
                                                     String mergedSearchContext,
                                                     String questionDirection) {
        String difficulty = session.getCurrentDifficulty() != null ? session.getCurrentDifficulty() : "BASIC";

        log.info("生成问题: sessionId={}, index={}, difficulty={}, perspective={}",
                session.getSessionId(), questionIndex, difficulty, perspectiveName);

        // 获取会话关联的知识库ID
        List<Long> knowledgeBaseIds = parseKnowledgeBaseIds(session.getKnowledgeBaseIds());

        // 从混合检索结果中提取知识库信息（如果有的话）
        Long usedKnowledgeBaseId = null;
        String knowledgeBaseName = null;
        String referenceContext = null;

        if (mergedSearchContext != null && !mergedSearchContext.isBlank()) {
            // 混合检索上下文已包含题库、知识库、网络搜索的内容
            // 格式为【题库题目-1】...【知识库内容-1】...【网络搜索-1】...
            referenceContext = mergedSearchContext;

            // 尝试从知识库检索结果中提取知识库ID和名称
            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                try {
                    // 获取第一个知识库作为主要使用的知识库
                    Optional<KnowledgeBaseEntity> kbOpt = knowledgeBaseRepository.findById(knowledgeBaseIds.getFirst());
                    if (kbOpt.isPresent()) {
                        usedKnowledgeBaseId = kbOpt.get().getId();
                        knowledgeBaseName = kbOpt.get().getName();
                    }
                } catch (Exception e) {
                    log.warn("获取知识库信息失败: {}", e.getMessage());
                }
            }

            log.info("使用混合检索上下文: sessionId={}, contextLength={}",
                    session.getSessionId(), mergedSearchContext.length());
        }

        // 过滤历史记录：只保留当前视角的 Q&A（隐私隔离）
        List<AnswerHistoryDTO> filteredHistory = history;
        if (perspectiveId != null && history != null && !history.isEmpty()) {
            filteredHistory = history.stream()
                    .filter(h -> perspectiveId.equals(h.createdByPerspectiveId()))
                    .toList();
            log.debug("历史过滤: 原始{}条 -> 过滤后{}条 (perspectiveId={})",
                    history.size(), filteredHistory.size(), perspectiveId);
        }

        // 使用AI生成，传入视角的prompt和过滤后的历史记录
        SimpleQuestionDTO aiResult = generateQuestionByAI(
                resumeText, difficulty, referenceContext, questionIndex,
                filteredHistory, perspectiveId, perspectivePrompt, perspectiveName, questionDirection);

        // 更新会话的 questionsGenerated 计数
        int currentGenerated = session.getQuestionsGenerated() != null ? session.getQuestionsGenerated() : 0;
        session.setQuestionsGenerated(currentGenerated + 1);

        // 使用AI返回的分类
        String finalCategory = aiResult.category();

        CurrentQuestionDTO dto = new CurrentQuestionDTO(
                questionIndex,
                aiResult.question(),
                finalCategory,
                difficulty,
                usedKnowledgeBaseId,
                knowledgeBaseName,
                referenceContext,
                aiResult.isFollowUp(),
                aiResult.relatedIndex(),
                aiResult.relatedQuestion(),
                perspectiveId,
                perspectiveName
        );

        log.info("问题生成成功: sessionId={}, index={}, category={}, questionLength={}, kbId={}, perspective={}",
                session.getSessionId(), questionIndex, finalCategory, aiResult.question().length(),
                usedKnowledgeBaseId, perspectiveName);

        return dto;
    }

    /**
     * 使用 AI 生成问题
     *
     * @return SimpleQuestionDTO 包含问题内容和分类
     */
    private SimpleQuestionDTO generateQuestionByAI(String resumeText, String difficulty,
                                                   String referenceContext, int questionIndex,
                                                   List<AnswerHistoryDTO> history,
                                                   Long perspectiveId, String perspectivePrompt,
                                                   String perspectiveName, String questionDirection) {
        try {
            // 确定系统提示词：优先使用视角prompt，否则使用通用难度prompt
            String systemPromptText = getSystemPromptText(difficulty, perspectivePrompt);
            log.debug("使用视角prompt出题: perspectiveId={}, perspectiveName={}, questionDirection={}", perspectiveId, perspectiveName, questionDirection);

            // 传入历史记录，让AI根据简历内容自行决定问题类型
            String userPromptText = buildUserPrompt(resumeText, difficulty, referenceContext, questionIndex,
                    history, perspectiveName, perspectivePrompt, questionDirection);

            // 调用 AI 生成
            SimpleQuestionDTO result = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPromptText,
                    userPromptText,
                    outputConverter,
                    ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                    "面试问题生成失败：",
                    "单个问题生成",
                    log
            );

            if (result != null && result.question() != null && !result.question().isBlank()) {
                return result;
            }

            // 降级处理：返回默认问题
            return generateDefaultQuestion();

        } catch (Exception e) {
            log.warn("AI问题生成失败，使用默认问题: {}", e.getMessage());
            return generateDefaultQuestion();
        }
    }

    @NotNull
    private String getSystemPromptText(String difficulty, String perspectivePrompt) {
        String basePrompt;
        if (perspectivePrompt != null && !perspectivePrompt.isBlank()) {
            basePrompt = perspectivePrompt;
        } else {
            basePrompt = "你是一个专业的技术面试官。请根据候选人的简历内容，自动选择一个最合适的技术方向来考察该候选人。";
        }
        // 系统提示词 = 角色定义 + 难度要求 + JSON格式说明
        String difficultyHint = getDifficultyHint(difficulty);
        return basePrompt + difficultyHint + """
                请以JSON格式返回，格式如下：
                {"question": "面试问题内容 - 生成的问题文本", "category": "问题分类 - 如Java基础/并发/数据库等", "isFollowUp": "是否追问 - true表示追问，false表示新问题", "relatedIndex": "全局问题索引 - 追问时填写关联问题的全局索引（即历史记录中【】内的数字，如问题2对应2）", "relatedQuestion": "关联问题摘要 - 追问时填写关联的问题摘要"}
                """;
    }

    /**
     * 获取难度提示
     */
    private String getDifficultyHint(String difficulty) {
        return switch (difficulty.toUpperCase()) {
            case "BASIC" -> "\n\n【难度要求】生成基础级别的问题，重点考察候选人对概念的理解和基本应用能力。";
            case "ADVANCED" -> "\n\n【难度要求】生成进阶级别的问题，重点考察候选人的深入理解和实际经验。";
            case "EXPERT" -> "\n\n【难度要求】生成专家级别的问题，重点考察候选人的深度认知、架构能力和问题解决能力。";
            default -> "";
        };
    }

    /**
     * 构建用户提示词
     * AI根据简历内容和历史记录自行决定问题类型和是否追问
     */
    private String buildUserPrompt(String resumeText, String difficulty,
                                   String referenceContext, int questionIndex,
                                   List<AnswerHistoryDTO> history,
                                   String perspectiveName,
                                   String perspectivePrompt,
                                   String questionDirection) {
        StringBuilder prompt = new StringBuilder();

        // 根据是否有视角信息决定面试官身份
        if (perspectiveName != null && !perspectiveName.isBlank()) {
            prompt.append("你是一位").append(perspectiveName).append("。");
            // 拼接面试官专门的出题prompt
            if (perspectivePrompt != null && !perspectivePrompt.isBlank()) {
                prompt.append(perspectivePrompt).append("\n\n");
            } else {
                prompt.append("请根据候选人的简历内容，从本角色的视角出发，考察候选人的相关能力。\n\n");
            }
        } else {
            prompt.append("你是一个专业的技术面试官。请根据候选人的简历内容，自动选择一个最合适的技术方向来考察该候选人。\n\n");
        }

        // 如果有出题方向，添加到提示词中
        if (questionDirection != null && !questionDirection.isBlank()) {
            prompt.append("【出题方向】").append(questionDirection).append("\n\n");
        }

        prompt.append("【问题索引】").append(questionIndex).append("\n");
        prompt.append("【难度等级】").append(difficulty).append("\n");
        prompt.append("【候选人简历】\n").append(resumeText).append("\n\n");

        // 添加历史答题记录
        if (history != null && !history.isEmpty()) {
            prompt.append("【历史答题记录】\n");
            for (AnswerHistoryDTO h : history) {
                prompt.append("- 问题").append(h.questionIndex()).append("（").append(h.category()).append("）：").append(h.question()).append("\n");
                prompt.append("  候选人回答：").append(h.userAnswer() != null ? h.userAnswer() : "无").append("\n");
                if (h.feedback() != null) {
                    prompt.append("  AI评价：").append(h.feedback()).append("\n");
                }
            }
            prompt.append("\n");

            // 告诉AI可以追问
            prompt.append("【追问规则】\n");
            prompt.append("如果发现候选人在某个问题上回答不完整或值得深入探讨，可以生成一道追问。\n");
            prompt.append("追问应该基于候选人上一题的回答内容进行深入追问，不要问与上一题无关的问题。\n");
            prompt.append("如果是追问，在 isFollowUp 填写 true，并在 relatedIndex 中填写关联问题的全局索引（即上面历史记录中【】内的数字，如追问你刚才出的题2则填2），在 relatedQuestion 中填写关联的问题摘要。\n\n");
        }

        if (referenceContext != null && !referenceContext.isBlank()) {
            prompt.append("【参考资料】\n").append(referenceContext).append("\n\n");
        }

        prompt.append("请根据简历内容，决定生成新问题还是追问（基于候选人的回答质量）。\n");
        prompt.append("生成一道具有针对性的面试问题或追问。");
        return prompt.toString();
    }

    /**
     * 生成默认问题（降级处理）
     */
    private SimpleQuestionDTO generateDefaultQuestion() {
        Map<String, String[]> defaultQuestions = new HashMap<>();
        defaultQuestions.put("Java基础", new String[]{
                "请解释什么是多态性？请举例说明。",
                "Java中的final关键字有哪些作用？",
                "请解释String、StringBuilder和StringBuffer的区别。"
        });
        defaultQuestions.put("Java集合", new String[]{
                "HashMap的底层实现原理是什么？",
                "ArrayList和LinkedList的区别是什么？",
                "请解释什么是 ConcurrentHashMap？"
        });
        defaultQuestions.put("Java并发", new String[]{
                "synchronized和ReentrantLock的区别是什么？",
                "线程池的核心参数有哪些？",
                "请解释volatile关键字的作用。"
        });
        defaultQuestions.put("MySQL", new String[]{
                "MySQL的索引有哪些类型？",
                "请解释什么是事务隔离级别？",
                "MySQL如何进行性能优化？"
        });
        defaultQuestions.put("Redis", new String[]{
                "Redis支持哪些数据结构？",
                "Redis的持久化机制有哪些？",
                "请解释Redis的缓存淘汰策略。"
        });
        defaultQuestions.put("Spring", new String[]{
                "Spring的IoC和AOP原理是什么？",
                "Spring Boot的自动配置原理是什么？",
                "请解释Spring Bean的生命周期。"
        });
        defaultQuestions.put("项目经历", new String[]{
                "请介绍一下你参与过的最有挑战性的项目？",
                "在项目中你遇到过最大的技术难题是什么？如何解决的？",
                "请介绍一下你负责的系统架构？"
        });

        // 随机选择一个分类
        String[] categories = {"Java基础", "Java集合", "Java并发", "MySQL", "Redis", "Spring", "项目经历"};
        String category = categories[(int) (Math.random() * categories.length)];
        String[] questions = defaultQuestions.get(category);
        int index = (int) (Math.random() * questions.length);
        return new SimpleQuestionDTO(questions[index], category, false, null, null);
    }

    /**
     * 解析知识库ID列表
     */
    private List<Long> parseKnowledgeBaseIds(String knowledgeBaseIdsJson) {
        if (knowledgeBaseIdsJson == null || knowledgeBaseIdsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(knowledgeBaseIdsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("解析知识库ID失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 中间DTO用于接收AI响应
     */
    private record SimpleQuestionDTO(
            String question,      // 问题内容（不含追问标记）
            String category,       // 分类
            Boolean isFollowUp,    // 是否是追问
            Integer relatedIndex,  // 关联的问题索引（追问时填写）
            String relatedQuestion // 关联的问题摘要（追问时填写）
    ) {
    }
}

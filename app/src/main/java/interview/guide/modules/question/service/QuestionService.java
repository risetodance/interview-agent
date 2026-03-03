package interview.guide.modules.question.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import interview.guide.common.result.Result;
import interview.guide.modules.question.enums.QuestionDifficulty;
import interview.guide.modules.question.model.QuestionDTO;
import interview.guide.modules.question.model.QuestionEntity;
import interview.guide.modules.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目服务
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;

    /**
     * 获取题库下的所有题目
     */
    public Result<List<QuestionDTO>> getQuestionsByBankId(Long bankId) {
        List<QuestionDTO> questions = questionRepository.findByQuestionBankId(bankId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return Result.success(questions);
    }

    /**
     * 分页获取题库下的题目
     */
    public Result<Page<QuestionDTO>> getQuestionsByBankId(Long bankId, int page, int size) {
        Page<QuestionEntity> pageEntity = questionRepository.findByQuestionBankId(bankId, PageRequest.of(page, size));
        Page<QuestionDTO> dtoPage = pageEntity.map(this::toDTO);
        return Result.success(dtoPage);
    }

    /**
     * 获取题目详情
     */
    public Result<QuestionDTO> getQuestionById(Long id) {
        return questionRepository.findById(id)
                .map(question -> Result.success(toDTO(question)))
                .orElse(Result.error("题目不存在"));
    }

    /**
     * 根据难度筛选题目
     */
    public Result<List<QuestionDTO>> getQuestionsByDifficulty(Long bankId, QuestionDifficulty difficulty) {
        List<QuestionDTO> questions = questionRepository.findByQuestionBankIdAndDifficulty(bankId, difficulty)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return Result.success(questions);
    }

    /**
     * 随机获取题目（用于面试）
     */
    public Result<List<QuestionDTO>> getRandomQuestions(Long bankId, int limit) {
        List<QuestionDTO> questions = questionRepository.findRandomQuestions(bankId, limit)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return Result.success(questions);
    }

    /**
     * 随机获取多个题库的题目
     */
    public Result<List<QuestionDTO>> getRandomQuestionsFromBanks(List<Long> bankIds, int limit) {
        if (bankIds == null || bankIds.isEmpty()) {
            return Result.error("请选择至少一个题库");
        }
        List<QuestionDTO> questions = questionRepository.findRandomQuestionsByBankIds(bankIds, limit)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return Result.success(questions);
    }

    /**
     * 创建题目
     */
    @Transactional
    public Result<QuestionDTO> createQuestion(QuestionDTO dto) {
        QuestionEntity entity = toEntity(dto);
        QuestionEntity saved = questionRepository.save(entity);

        // 更新题库题目数量
        updateBankQuestionCount(dto.getQuestionBankId());

        return Result.success(toDTO(saved));
    }

    /**
     * 批量创建题目
     */
    @Transactional
    public Result<Integer> batchCreateQuestions(List<QuestionDTO> dtos, Long bankId) {
        List<QuestionEntity> entities = dtos.stream()
                .map(dto -> {
                    dto.setQuestionBankId(bankId);
                    return toEntity(dto);
                })
                .collect(Collectors.toList());

        questionRepository.saveAll(entities);

        // 更新题库题目数量
        updateBankQuestionCount(bankId);

        return Result.success(entities.size());
    }

    /**
     * 更新题目
     */
    @Transactional
    public Result<QuestionDTO> updateQuestion(Long id, QuestionDTO dto) {
        QuestionEntity entity = questionRepository.findById(id)
                .orElse(null);

        if (entity == null) {
            return Result.error("题目不存在");
        }

        if (dto.getContent() != null) {
            entity.setContent(dto.getContent());
        }
        if (dto.getAnswer() != null) {
            entity.setAnswer(dto.getAnswer());
        }
        if (dto.getDifficulty() != null) {
            entity.setDifficulty(dto.getDifficulty());
        }
        if (dto.getTags() != null) {
            entity.setTags(toJson(dto.getTags()));
        }

        QuestionEntity saved = questionRepository.save(entity);
        return Result.success(toDTO(saved));
    }

    /**
     * 删除题目
     */
    @Transactional
    public Result<Void> deleteQuestion(Long id) {
        QuestionEntity entity = questionRepository.findById(id)
                .orElse(null);

        if (entity == null) {
            return Result.error("题目不存在");
        }

        Long bankId = entity.getQuestionBankId();
        questionRepository.delete(entity);

        // 更新题库题目数量
        updateBankQuestionCount(bankId);

        return Result.success(null);
    }

    private void updateBankQuestionCount(Long bankId) {
        long count = questionRepository.countByQuestionBankId(bankId);
        questionBankService.updateQuestionCount(bankId, (int) count);
    }

    private QuestionDTO toDTO(QuestionEntity entity) {
        return QuestionDTO.builder()
                .id(entity.getId())
                .questionBankId(entity.getQuestionBankId())
                .content(entity.getContent())
                .answer(entity.getAnswer())
                .difficulty(entity.getDifficulty())
                .tags(parseTags(entity.getTags()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private QuestionEntity toEntity(QuestionDTO dto) {
        return QuestionEntity.builder()
                .id(dto.getId())
                .questionBankId(dto.getQuestionBankId())
                .content(dto.getContent())
                .answer(dto.getAnswer())
                .difficulty(dto.getDifficulty() != null ? dto.getDifficulty() : QuestionDifficulty.MEDIUM)
                .tags(dto.getTags() != null ? toJson(dto.getTags()) : null)
                .build();
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            return Collections.emptyList();
        }
    }

    private String toJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JacksonException e) {
            return null;
        }
    }
}

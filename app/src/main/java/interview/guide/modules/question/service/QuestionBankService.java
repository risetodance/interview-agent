package interview.guide.modules.question.service;

import interview.guide.common.result.Result;
import interview.guide.modules.question.enums.QuestionBankType;
import interview.guide.modules.question.model.QuestionBankDTO;
import interview.guide.modules.question.model.QuestionBankEntity;
import interview.guide.modules.question.repository.QuestionBankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 题库服务
 */
@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private final QuestionBankRepository questionBankRepository;

    /**
     * 获取所有系统预置题库
     */
    public Result<List<QuestionBankDTO>> getSystemBanks() {
        List<QuestionBankDTO> banks = questionBankRepository.findByType(QuestionBankType.SYSTEM)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return Result.success(banks);
    }

    /**
     * 获取用户的题库列表（包括系统预置）
     */
    public Result<List<QuestionBankDTO>> getUserBanks(Long userId) {
        List<QuestionBankDTO> banks = questionBankRepository.findByUserIdOrSystem(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return Result.success(banks);
    }

    /**
     * 获取用户自定义题库
     */
    public Result<List<QuestionBankDTO>> getMyBanks(Long userId) {
        List<QuestionBankDTO> banks = questionBankRepository.findUserBanks(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return Result.success(banks);
    }

    /**
     * 获取题库详情
     */
    public Result<QuestionBankDTO> getBankById(Long id) {
        return questionBankRepository.findById(id)
                .map(bank -> Result.success(toDTO(bank)))
                .orElse(Result.error("题库不存在"));
    }

    /**
     * 创建题库
     */
    @Transactional
    public Result<QuestionBankDTO> createBank(QuestionBankDTO dto, Long userId) {
        // 检查题库名称是否已存在
        if (questionBankRepository.existsByNameAndUserIdAndType(dto.getName(), userId, QuestionBankType.USER)) {
            return Result.error("题库名称已存在");
        }

        QuestionBankEntity entity = QuestionBankEntity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .type(QuestionBankType.USER)
                .userId(userId)
                .questionCount(0)
                .build();

        QuestionBankEntity saved = questionBankRepository.save(entity);
        return Result.success(toDTO(saved));
    }

    /**
     * 更新题库
     */
    @Transactional
    public Result<QuestionBankDTO> updateBank(Long id, QuestionBankDTO dto, Long userId) {
        QuestionBankEntity entity = questionBankRepository.findById(id)
                .orElse(null);

        if (entity == null) {
            return Result.error("题库不存在");
        }

        // 只能修改自己的题库
        if (entity.getType() == QuestionBankType.USER && !entity.getUserId().equals(userId)) {
            return Result.error("无权限修改此题库");
        }

        if (dto.getName() != null) {
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }

        QuestionBankEntity saved = questionBankRepository.save(entity);
        return Result.success(toDTO(saved));
    }

    /**
     * 删除题库
     */
    @Transactional
    public Result<Void> deleteBank(Long id, Long userId) {
        QuestionBankEntity entity = questionBankRepository.findById(id)
                .orElse(null);

        if (entity == null) {
            return Result.error("题库不存在");
        }

        // 只能删除自己的题库
        if (entity.getType() == QuestionBankType.USER && !entity.getUserId().equals(userId)) {
            return Result.error("无权限删除此题库");
        }

        questionBankRepository.delete(entity);
        return Result.success(null);
    }

    /**
     * 更新题库题目数量
     */
    @Transactional
    public void updateQuestionCount(Long bankId, int count) {
        questionBankRepository.findById(bankId).ifPresent(bank -> {
            bank.setQuestionCount(count);
            questionBankRepository.save(bank);
        });
    }

    private QuestionBankDTO toDTO(QuestionBankEntity entity) {
        return QuestionBankDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .userId(entity.getUserId())
                .questionCount(entity.getQuestionCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

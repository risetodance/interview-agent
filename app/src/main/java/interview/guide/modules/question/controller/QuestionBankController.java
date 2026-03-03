package interview.guide.modules.question.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.result.Result;
import interview.guide.modules.question.model.QuestionBankDTO;
import interview.guide.modules.question.service.QuestionBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题库控制器
 */
@RestController
@RequestMapping("/api/question-banks")
@RequiredArgsConstructor
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    /**
     * 获取所有系统预置题库
     */
    @GetMapping("/system")
    public Result<List<QuestionBankDTO>> getSystemBanks() {
        return questionBankService.getSystemBanks();
    }

    /**
     * 获取用户的题库列表（包括系统预置）
     */
    @GetMapping
    public Result<List<QuestionBankDTO>> getUserBanks(@CurrentUser Long userId) {
        return questionBankService.getUserBanks(userId);
    }

    /**
     * 获取用户自定义题库
     */
    @GetMapping("/my")
    public Result<List<QuestionBankDTO>> getMyBanks(@CurrentUser Long userId) {
        return questionBankService.getMyBanks(userId);
    }

    /**
     * 获取题库详情
     */
    @GetMapping("/{id}")
    public Result<QuestionBankDTO> getBankById(@PathVariable Long id) {
        return questionBankService.getBankById(id);
    }

    /**
     * 创建题库
     */
    @PostMapping
    public Result<QuestionBankDTO> createBank(
            @RequestBody QuestionBankDTO dto,
            @CurrentUser Long userId) {
        return questionBankService.createBank(dto, userId);
    }

    /**
     * 更新题库
     */
    @PutMapping("/{id}")
    public Result<QuestionBankDTO> updateBank(
            @PathVariable Long id,
            @RequestBody QuestionBankDTO dto,
            @CurrentUser Long userId) {
        return questionBankService.updateBank(id, dto, userId);
    }

    /**
     * 删除题库
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteBank(
            @PathVariable Long id,
            @CurrentUser Long userId) {
        return questionBankService.deleteBank(id, userId);
    }
}

package interview.guide.modules.question.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.question.enums.QuestionDifficulty;
import interview.guide.modules.question.model.QuestionDTO;
import interview.guide.modules.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题目控制器
 */
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /**
     * 获取题库下的所有题目
     */
    @GetMapping("/bank/{bankId}")
    public Result<List<QuestionDTO>> getQuestionsByBankId(@PathVariable Long bankId) {
        return questionService.getQuestionsByBankId(bankId);
    }

    /**
     * 分页获取题库下的题目
     */
    @GetMapping("/bank/{bankId}/page")
    public Result<Page<QuestionDTO>> getQuestionsByBankId(
            @PathVariable Long bankId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return questionService.getQuestionsByBankId(bankId, page, size);
    }

    /**
     * 获取题目详情
     */
    @GetMapping("/{id}")
    public Result<QuestionDTO> getQuestionById(@PathVariable Long id) {
        return questionService.getQuestionById(id);
    }

    /**
     * 根据难度筛选题目
     */
    @GetMapping("/bank/{bankId}/difficulty/{difficulty}")
    public Result<List<QuestionDTO>> getQuestionsByDifficulty(
            @PathVariable Long bankId,
            @PathVariable QuestionDifficulty difficulty) {
        return questionService.getQuestionsByDifficulty(bankId, difficulty);
    }

    /**
     * 随机获取题目（用于面试）
     */
    @GetMapping("/bank/{bankId}/random")
    public Result<List<QuestionDTO>> getRandomQuestions(
            @PathVariable Long bankId,
            @RequestParam(defaultValue = "5") int limit) {
        return questionService.getRandomQuestions(bankId, limit);
    }

    /**
     * 从多个题库随机获取题目
     */
    @GetMapping("/banks/random")
    public Result<List<QuestionDTO>> getRandomQuestionsFromBanks(
            @RequestParam List<Long> bankIds,
            @RequestParam(defaultValue = "5") int limit) {
        return questionService.getRandomQuestionsFromBanks(bankIds, limit);
    }

    /**
     * 创建题目
     */
    @PostMapping
    public Result<QuestionDTO> createQuestion(@RequestBody QuestionDTO dto) {
        return questionService.createQuestion(dto);
    }

    /**
     * 批量创建题目
     */
    @PostMapping("/batch")
    public Result<Integer> batchCreateQuestions(
            @RequestBody List<QuestionDTO> dtos,
            @RequestParam Long bankId) {
        return questionService.batchCreateQuestions(dtos, bankId);
    }

    /**
     * 更新题目
     */
    @PutMapping("/{id}")
    public Result<QuestionDTO> updateQuestion(
            @PathVariable Long id,
            @RequestBody QuestionDTO dto) {
        return questionService.updateQuestion(id, dto);
    }

    /**
     * 删除题目
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteQuestion(@PathVariable Long id) {
        return questionService.deleteQuestion(id);
    }
}

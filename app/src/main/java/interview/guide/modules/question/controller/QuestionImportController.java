package interview.guide.modules.question.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.question.model.QuestionDTO;
import interview.guide.modules.question.service.QuestionImportService;
import interview.guide.modules.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 题目导入控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/questions/import")
@RequiredArgsConstructor
public class QuestionImportController {

    private final QuestionImportService questionImportService;
    private final QuestionService questionService;

    /**
     * 从 Excel 文件导入题目
     *
     * @param file   Excel 文件 (.xlsx, .xls)
     * @param bankId 题库ID
     * @return 导入结果
     */
    @PostMapping("/excel")
    public Result<Integer> importFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bankId") Long bankId) {

        log.info("从 Excel 导入题目到题库: bankId={}, filename={}", bankId, file.getOriginalFilename());

        // 解析 Excel 文件
        List<QuestionDTO> questions = questionImportService.parseExcel(file);

        // 保存到数据库
        return questionService.batchCreateQuestions(questions, bankId);
    }

    /**
     * 从 Markdown 内容导入题目
     *
     * @param content Markdown 内容
     * @param bankId  题库ID
     * @return 导入结果
     */
    @PostMapping("/markdown")
    public Result<Integer> importFromMarkdown(
            @RequestBody ImportMarkdownRequest request,
            @RequestParam("bankId") Long bankId) {

        log.info("从 Markdown 导入题目到题库: bankId={}", bankId);

        // 解析 Markdown 内容
        List<QuestionDTO> questions = questionImportService.parseMarkdown(request.getContent());

        // 保存到数据库
        return questionService.batchCreateQuestions(questions, bankId);
    }

    /**
     * 预览导入内容（不保存）
     *
     * @param file Excel 文件
     * @return 预览结果
     */
    @PostMapping("/preview/excel")
    public Result<List<QuestionDTO>> previewExcel(@RequestParam("file") MultipartFile file) {
        List<QuestionDTO> questions = questionImportService.parseExcel(file);
        return Result.success(questions);
    }

    /**
     * 预览 Markdown 内容（不保存）
     *
     * @param content Markdown 内容
     * @return 预览结果
     */
    @PostMapping("/preview/markdown")
    public Result<List<QuestionDTO>> previewMarkdown(@RequestBody ImportMarkdownRequest request) {
        List<QuestionDTO> questions = questionImportService.parseMarkdown(request.getContent());
        return Result.success(questions);
    }

    /**
     * Markdown 导入请求
     */
    public static class ImportMarkdownRequest {
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}

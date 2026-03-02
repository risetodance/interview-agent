package interview.guide.modules.resume;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.resume.model.ResumeDetailDTO;
import interview.guide.modules.resume.model.ResumeListItemDTO;
import interview.guide.modules.resume.service.ResumeDeleteService;
import interview.guide.modules.resume.service.ResumeHistoryService;
import interview.guide.modules.resume.service.ResumeUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 简历控制器
 * Resume Controller for upload and analysis
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeUploadService uploadService;
    private final ResumeDeleteService deleteService;
    private final ResumeHistoryService historyService;

    /**
     * 上传简历并获取分析结果
     *
     * @param file   简历文件（支持PDF、DOCX、DOC、TXT）
     * @param userId 当前登录用户ID
     * @return 简历分析结果，包含评分和建议
     */
    @PostMapping(value = "/api/resumes/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 5)
    public Result<Map<String, Object>> uploadAndAnalyze(
            @RequestParam("file") MultipartFile file,
            @CurrentUser Long userId) {
        Map<String, Object> result = uploadService.uploadAndAnalyze(file, userId);
        boolean isDuplicate = (Boolean) result.get("duplicate");
        if (isDuplicate) {
            return Result.success("检测到相同简历，已返回历史分析结果", result);
        }
        return Result.success(result);
    }

    /**
     * 获取当前用户的简历列表
     *
     * @param userId 当前登录用户ID
     * @return 简历列表
     */
    @GetMapping("/api/resumes")
    public Result<List<ResumeListItemDTO>> getAllResumes(@CurrentUser Long userId) {
        List<ResumeListItemDTO> resumes = historyService.getAllResumes(userId);
        return Result.success(resumes);
    }

    /**
     * 获取简历详情（包含分析历史）
     *
     * @param id     简历ID
     * @param userId 当前登录用户ID
     * @return 简历详情
     */
    @GetMapping("/api/resumes/{id}/detail")
    public Result<ResumeDetailDTO> getResumeDetail(
            @PathVariable Long id,
            @CurrentUser Long userId) {
        ResumeDetailDTO detail = historyService.getResumeDetail(id, userId);
        return Result.success(detail);
    }

    /**
     * 导出简历分析报告为PDF
     *
     * @param id     简历ID
     * @param userId 当前登录用户ID
     * @return PDF文件
     */
    @GetMapping("/api/resumes/{id}/export")
    public ResponseEntity<byte[]> exportAnalysisPdf(
            @PathVariable Long id,
            @CurrentUser Long userId) {
        try {
            var result = historyService.exportAnalysisPdf(id, userId);
            String filename = URLEncoder.encode(result.filename(), StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(result.pdfBytes());
        } catch (Exception e) {
            log.error("导出PDF失败: resumeId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除简历
     *
     * @param id     简历ID
     * @param userId 当前登录用户ID
     * @return 删除结果
     */
    @DeleteMapping("/api/resumes/{id}")
    public Result<Void> deleteResume(
            @PathVariable Long id,
            @CurrentUser Long userId) {
        deleteService.deleteResume(id, userId);
        return Result.success(null);
    }

    /**
     * 重新分析简历（手动重试）
     * 用于分析失败后的重试
     *
     * @param id     简历ID
     * @param userId 当前登录用户ID
     * @return 结果
     */
    @PostMapping("/api/resumes/{id}/reanalyze")
    @RateLimit(dimensions = {RateLimit.Dimension.GLOBAL, RateLimit.Dimension.IP}, count = 2)
    public Result<Void> reanalyze(
            @PathVariable Long id,
            @CurrentUser Long userId) {
        uploadService.reanalyze(id, userId);
        return Result.success(null);
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/api/resumes/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
            "status", "UP",
            "service", "AI Interview Platform - Resume Service"
        ));
    }
    
}

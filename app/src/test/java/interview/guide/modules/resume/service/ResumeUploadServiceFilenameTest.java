package interview.guide.modules.resume.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历文件名兜底检测测试：小程序临时文件名识别（重新上传/上传的文件名兜底依赖此规则）
 */
@DisplayName("临时文件名检测测试")
class ResumeUploadServiceFilenameTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "未命名文件", "tmp_ab12cd34.pdf", "tmp-9f8e7d6c5b4a.pdf",
            "tmpab12cd34ef56", "wxfile://tmp/xxx.pdf", "wxfile_tmp_xxx",
            "5f8a9b3c7d2e1f0a8b7c6d5e.pdf", "A3F5E7D9C1B4A6F8E2D0C4B6A8F2E4D6"})
    @DisplayName("空/未命名/小程序临时文件名特征均判为无效")
    void tempFilenames(String input) {
        assertTrue(ResumeUploadService.isTempFilename(input), "应识别为无效文件名: " + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"简历-开科版.pdf", "我的简历2026.pdf", "tmp整理的简历.pdf",
            "tmp_简历.pdf", "resume_final.pdf", "张三-Java工程师-2026.docx", "3页简历.txt"})
    @DisplayName("合法文件名（含 tmp 开头的中文短名）不误伤")
    void validFilenames(String input) {
        assertFalse(ResumeUploadService.isTempFilename(input), "不应误判为临时文件名: " + input);
    }
}

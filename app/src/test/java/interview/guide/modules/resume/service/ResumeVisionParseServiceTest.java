package interview.guide.modules.resume.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历视觉识别服务：文本有效性阈值判断（上传拦截与消费者兜底的共用标准）+ 识图模板可渲染守护
 */
@DisplayName("简历文本有效性阈值测试")
class ResumeVisionParseServiceTest {

    @Test
    @DisplayName("识图 system 模板可正常渲染（含 JSON 转义示例，守护 StringTemplate 语法）")
    void visionSystemPromptRenders() {
        PromptTemplate template = new PromptTemplate(
                new ClassPathResource("prompts/resume-vision-parse-system.st"));
        String rendered = template.render();
        assertTrue(rendered.contains("转义示例"), "模板应包含转义示例段");
        assertTrue(rendered.contains("\\n"), "示例应包含换行转义写法");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " \n\t ", "abc"})
    @DisplayName("null / 空白 / 少于 100 字符均视为无效，触发视觉兜底")
    void insufficientText(String input) {
        assertTrue(ResumeVisionParseService.isTextInsufficient(input));
    }

    @Test
    @DisplayName("达到 100 字符（trim 后）视为有效，走既有文本链路")
    void sufficientText() {
        String text = "a".repeat(ResumeVisionParseService.MIN_EFFECTIVE_TEXT_LENGTH);
        assertFalse(ResumeVisionParseService.isTextInsufficient(text));
        // 前后空白不计入
        String padded = "   " + "a".repeat(ResumeVisionParseService.MIN_EFFECTIVE_TEXT_LENGTH - 1) + "   ";
        assertTrue(ResumeVisionParseService.isTextInsufficient(padded));
    }
}

package interview.guide.infrastructure.file;

import interview.guide.common.exception.BusinessException;
import interview.guide.infrastructure.file.PdfPageImageRenderer.RenderedPages;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 页面渲染器单元测试：页数上限截断与损坏文件处理
 */
@DisplayName("PDF 页面渲染器测试")
class PdfPageImageRendererTest {

    private final PdfPageImageRenderer renderer = new PdfPageImageRenderer();

    /** 生成指定页数的空白 PDF 字节 */
    private byte[] blankPdf(int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("正常 PDF：逐页渲染 PNG，不截断")
    void renderNormalPdf() throws IOException {
        RenderedPages pages = renderer.render(blankPdf(3));

        assertEquals(3, pages.pageImages().size());
        assertEquals(3, pages.totalPages());
        assertFalse(pages.truncated());
        pages.pageImages().forEach(image -> assertTrue(image.length > 0, "每页应渲染出非空 PNG"));
    }

    @Test
    @DisplayName("超出 15 页上限：仅渲染前 15 页并标记截断")
    void renderTruncatesAtMaxPages() throws IOException {
        RenderedPages pages = renderer.render(blankPdf(20));

        assertEquals(PdfPageImageRenderer.MAX_PAGES, pages.pageImages().size());
        assertEquals(20, pages.totalPages());
        assertTrue(pages.truncated());
    }

    @Test
    @DisplayName("正好 15 页：不截断（边界含等于）")
    void renderExactlyMaxPages() throws IOException {
        RenderedPages pages = renderer.render(blankPdf(15));

        assertEquals(15, pages.pageImages().size());
        assertFalse(pages.truncated());
    }

    @Test
    @DisplayName("损坏的字节：抛业务异常而非裸 IOException")
    void renderCorruptedBytes() {
        assertThrows(BusinessException.class, () -> renderer.render("not a pdf".getBytes()));
    }
}

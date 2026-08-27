package interview.guide.infrastructure.file;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 页面渲染器：把 PDF 每页渲染为 PNG 图片字节，供视觉（多模态）模型识图使用。
 * <p>基于 PDFBox（Tika 传递依赖引入，无需额外依赖）。headless 环境可正常渲染。
 * <p>页数上限 {@link #MAX_PAGES}：超出截断仅渲染前 N 页，调用方负责向识别结果标注截断。
 */
@Slf4j
@Service
public class PdfPageImageRenderer {

    /** 单份 PDF 最大识别页数：正常简历 1~3 页，15 页已远超合理范围，超出截断兜住视觉调用成本 */
    public static final int MAX_PAGES = 15;

    /** 渲染 DPI：150 下 A4 约 1240×1754px，识图清晰度与图片 token 成本的平衡点 */
    private static final float RENDER_DPI = 150;

    /**
     * 渲染结果：pageImages 按页序排列的 PNG 字节，totalPages 原始总页数，truncated 是否因超上限截断。
     */
    public record RenderedPages(List<byte[]> pageImages, int totalPages, boolean truncated) {}

    /**
     * 把 PDF 字节渲染为逐页 PNG 图片。
     *
     * @param pdfBytes PDF 文件字节
     * @return 按页序的 PNG 图片列表（最多 {@link #MAX_PAGES} 页）
     * @throws BusinessException PDF 损坏 / 无法渲染
     */
    public RenderedPages render(byte[] pdfBytes) {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            int pagesToRender = Math.min(totalPages, MAX_PAGES);
            PDFRenderer renderer = new PDFRenderer(document);
            List<byte[]> images = new ArrayList<>(pagesToRender);
            for (int pageIndex = 0; pageIndex < pagesToRender; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                images.add(out.toByteArray());
            }
            boolean truncated = totalPages > MAX_PAGES;
            if (truncated) {
                log.warn("PDF 共 {} 页，超出识别上限 {} 页，仅渲染前 {} 页", totalPages, MAX_PAGES, MAX_PAGES);
            }
            log.info("PDF 渲染完成: 总页数={}, 渲染页数={}", totalPages, images.size());
            return new RenderedPages(images, totalPages, truncated);
        } catch (IOException e) {
            log.error("PDF 渲染为图片失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PDF 渲染为图片失败: " + e.getMessage());
        }
    }
}

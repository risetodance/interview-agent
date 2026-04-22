package interview.guide.modules.knowledgebase.service;

import interview.guide.common.config.ChunkingProperties;
import interview.guide.modules.knowledgebase.model.ParentDocumentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticChunkingServiceTest {

    private SemanticChunkingService service;

    @BeforeEach
    void setUp() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setParentMaxTokens(1200);
        properties.setChildMaxTokens(300);
        service = new SemanticChunkingService(properties);
    }

    @Test
    void testSplitIntoParents_simpleParagraphs() {
        String content = "第一段内容。\n\n第二段内容。\n\n第三段内容。";
        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, content);

        // 三个小段落应合并为一个parent（总token小于1200）
        assertEquals(1, parents.size());
        assertTrue(parents.get(0).getContent().contains("第一段"));
        assertTrue(parents.get(0).getContent().contains("第二段"));
        assertTrue(parents.get(0).getContent().contains("第三段"));
    }

    @Test
    void testSplitIntoParents_emptyContent() {
        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, "");
        assertTrue(parents.isEmpty());

        parents = service.splitIntoParents(1L, null);
        assertTrue(parents.isEmpty());
    }

    @Test
    void testSplitIntoParents_mergeSmallAdjacentParagraphs() {
        // 两个小段落合并为一个parent
        String content = "第一段内容。\n\n第二段内容。";
        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, content);

        assertEquals(1, parents.size());
        assertTrue(parents.get(0).getContent().contains("第一段"));
        assertTrue(parents.get(0).getContent().contains("第二段"));
    }

    @Test
    void testSplitIntoParents_accumulateThenExceed() {
        // 段落1(约600t) + 段落2(约700t) > 1200t，应分为2个parent
        StringBuilder sb1 = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            sb1.append("测试文本第").append(i).append("项内容，");
        }
        String p1 = sb1.toString();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb2.append("内容数据第").append(i).append("条信息，");
        }
        String p2 = sb2.toString();
        String content = p1 + "\n\n" + p2;

        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, content);

        assertEquals(2, parents.size());
    }

    @Test
    void testSplitIntoParents_multipleSmallMerge() {
        // 三个小段落合并为一个parent
        String content = "第一段。\n\n第二段。\n\n第三段。";
        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, content);

        assertEquals(1, parents.size());
        assertTrue(parents.get(0).getContent().contains("第一段"));
        assertTrue(parents.get(0).getContent().contains("第二段"));
        assertTrue(parents.get(0).getContent().contains("第三段"));
    }

    @Test
    void testSplitIntoParents_largeParagraphSplits() {
        // 构造超过1200token的大段落（一个段落内有多个换行）
        // 需要超过245次才能超过1200 tokens
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("测试文本第").append(i).append("行内容，包含足够多的中文字符。\n");
        }
        String content = sb.toString(); // 整个是一个大段落（因为没有\n\n分隔）
        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, content);

        assertTrue(parents.size() > 1);
    }

    @Test
    void testSplitIntoChildren() {
        // 先创建 Parent
        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, "这是第一个句子。这是第二个句子。这是第三个句子。");

        // 为 Parent 设置 ID（实际场景中由数据库生成）
        for (int i = 0; i < parents.size(); i++) {
            parents.get(i).setId((long) (i + 1));
        }

        List<Document> children = service.splitIntoChildren(parents);

        assertFalse(children.isEmpty());
        // 验证 metadata 包含 parent_id
        for (Document child : children) {
            assertTrue(child.getMetadata().containsKey("parent_id"));
            assertTrue(child.getMetadata().containsKey("kb_id"));
        }
    }
}
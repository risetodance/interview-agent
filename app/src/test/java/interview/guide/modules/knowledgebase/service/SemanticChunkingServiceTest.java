package interview.guide.modules.knowledgebase.service;

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
        service = new SemanticChunkingService();
    }

    @Test
    void testSplitIntoParents_simpleParagraphs() {
        String content = "第一段内容。\n\n第二段内容。\n\n第三段内容。";
        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, content);

        assertEquals(3, parents.size());
        assertEquals("第一段内容。", parents.get(0).getContent());
        assertEquals("第二段内容。", parents.get(1).getContent());
        assertEquals("第三段内容。", parents.get(2).getContent());
    }

    @Test
    void testSplitIntoParents_emptyContent() {
        List<ParentDocumentEntity> parents = service.splitIntoParents(1L, "");
        assertTrue(parents.isEmpty());

        parents = service.splitIntoParents(1L, null);
        assertTrue(parents.isEmpty());
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

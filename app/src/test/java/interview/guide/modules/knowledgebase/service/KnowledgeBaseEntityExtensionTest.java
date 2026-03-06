package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 知识库扩展功能测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>isPublic 字段 - 知识库公开/私有设置</li>
 *   <li>usageCount 字段 - 被引用次数</li>
 *   <li>incrementUsageCount 方法 - 增加引用次数</li>
 * </ul>
 */
@DisplayName("知识库扩展功能测试")
class KnowledgeBaseEntityExtensionTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("测试知识库实体默认值为非公开")
    void testKnowledgeBaseDefaultIsPublic() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        assertFalse(entity.getIsPublic(), "默认应该为非公开");
        assertEquals(0, entity.getUsageCount(), "默认引用次数为0");
    }

    @Test
    @DisplayName("测试设置知识库为公开")
    void testSetKnowledgeBasePublic() {
        KnowledgeBaseEntity entity = createTestEntity();

        entity.setIsPublic(true);

        assertTrue(entity.getIsPublic());
    }

    @Test
    @DisplayName("测试增加引用次数")
    void testIncrementUsageCount() {
        KnowledgeBaseEntity entity = createTestEntity();

        entity.incrementUsageCount();
        entity.incrementUsageCount();
        entity.incrementUsageCount();

        assertEquals(3, entity.getUsageCount());
    }

    @Test
    @DisplayName("测试 Repository 查询公开知识库")
    void testFindPublicKnowledgeBases() {
        List<KnowledgeBaseEntity> publicKBs = List.of(
                createPublicKB(1L, "Java面试题", 10),
                createPublicKB(2L, "Python面试题", 5)
        );

        when(knowledgeBaseRepository.findByIsPublicTrueOrderByUsageCountDesc())
                .thenReturn(publicKBs);

        List<KnowledgeBaseEntity> result = knowledgeBaseRepository.findByIsPublicTrueOrderByUsageCountDesc();

        assertEquals(2, result.size());
        assertTrue(result.get(0).getIsPublic());
        assertEquals(10, result.get(0).getUsageCount());
    }

    @Test
    @DisplayName("测试 Repository 搜索公开知识库")
    void testSearchPublicKnowledgeBases() {
        List<KnowledgeBaseEntity> searchResults = List.of(
                createPublicKB(1L, "Java面试题", 10)
        );

        when(knowledgeBaseRepository.searchPublicByKeyword("Java"))
                .thenReturn(searchResults);

        List<KnowledgeBaseEntity> result = knowledgeBaseRepository.searchPublicByKeyword("Java");

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsPublic());
    }

    @Test
    @DisplayName("测试 Repository 增加引用次数")
    void testIncrementUsageCountInRepository() {
        when(knowledgeBaseRepository.incrementUsageCount(1L))
                .thenReturn(1);

        int updated = knowledgeBaseRepository.incrementUsageCount(1L);

        assertEquals(1, updated);
        verify(knowledgeBaseRepository, times(1)).incrementUsageCount(1L);
    }

    @Test
    @DisplayName("测试根据ID和公开状态查询知识库")
    void testFindByIdAndIsPublicTrue() {
        KnowledgeBaseEntity publicKB = createPublicKB(1L, "公开知识库", 5);

        when(knowledgeBaseRepository.findByIdAndIsPublicTrue(1L))
                .thenReturn(Optional.of(publicKB));

        Optional<KnowledgeBaseEntity> result = knowledgeBaseRepository.findByIdAndIsPublicTrue(1L);

        assertTrue(result.isPresent());
        assertEquals("公开知识库", result.get().getName());
    }

    // 辅助方法：创建测试用知识库实体
    private KnowledgeBaseEntity createTestEntity() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setName("测试知识库");
        entity.setOriginalFilename("test.pdf");
        entity.setFileHash("abc123");
        entity.setUserId(100L);
        entity.setIsPublic(false);
        entity.setUsageCount(0);
        entity.setUploadedAt(LocalDateTime.now());
        entity.setVectorStatus(VectorStatus.COMPLETED);
        return entity;
    }

    // 辅助方法：创建公开知识库实体
    private KnowledgeBaseEntity createPublicKB(Long id, String name, int usageCount) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setOriginalFilename(name + ".pdf");
        entity.setFileHash("hash" + id);
        entity.setUserId(100L);
        entity.setIsPublic(true);
        entity.setUsageCount(usageCount);
        entity.setUploadedAt(LocalDateTime.now());
        entity.setVectorStatus(VectorStatus.COMPLETED);
        return entity;
    }
}

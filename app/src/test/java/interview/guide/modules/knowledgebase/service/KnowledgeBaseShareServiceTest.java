package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.mapper.KnowledgeBaseMapper;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.membership.service.PointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 知识库共享服务测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>setVisibility - 设置知识库公开/私有</li>
 *   <li>listPublicKnowledgeBases - 获取公开知识库列表</li>
 *   <li>searchPublicKnowledgeBases - 搜索公开知识库</li>
 *   <li>referenceKnowledgeBase - 引用公开知识库</li>
 * </ul>
 */
@DisplayName("知识库共享服务测试")
class KnowledgeBaseShareServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private PointsService pointsService;

    @InjectMocks
    private KnowledgeBaseShareService shareService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("测试设置知识库为公开")
    void testSetVisibilityToPublic() {
        // Arrange
        Long userId = 1L;
        Long kbId = 100L;
        KnowledgeBaseEntity entity = createPrivateKB(kbId, userId, "测试知识库");
        KnowledgeBaseListItemDTO dto = createDTO(kbId, "测试知识库", true);

        when(knowledgeBaseRepository.findByIdAndUserId(kbId, userId))
                .thenReturn(Optional.of(entity));
        when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class)))
                .thenReturn(entity);
        when(knowledgeBaseMapper.toListItemDTO(any(KnowledgeBaseEntity.class)))
                .thenReturn(dto);

        // Act
        KnowledgeBaseListItemDTO result = shareService.setVisibility(userId, kbId, true);

        // Assert
        assertNotNull(result);
        assertTrue(result.isPublic());
        verify(knowledgeBaseRepository).save(any(KnowledgeBaseEntity.class));
        verify(pointsService).shareKnowledgeBase(userId, kbId);
    }

    @Test
    @DisplayName("测试设置知识库为私有")
    void testSetVisibilityToPrivate() {
        // Arrange
        Long userId = 1L;
        Long kbId = 100L;
        KnowledgeBaseEntity entity = createPublicKB(kbId, userId, "测试知识库");
        KnowledgeBaseListItemDTO dto = createDTO(kbId, "测试知识库", false);

        when(knowledgeBaseRepository.findByIdAndUserId(kbId, userId))
                .thenReturn(Optional.of(entity));
        when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class)))
                .thenReturn(entity);
        when(knowledgeBaseMapper.toListItemDTO(any(KnowledgeBaseEntity.class)))
                .thenReturn(dto);

        // Act
        KnowledgeBaseListItemDTO result = shareService.setVisibility(userId, kbId, false);

        // Assert
        assertNotNull(result);
        assertFalse(result.isPublic());
        verify(knowledgeBaseRepository).save(any(KnowledgeBaseEntity.class));
        // 私有化时不奖励积分
        verify(pointsService, never()).shareKnowledgeBase(anyLong(), anyLong());
    }

    @Test
    @DisplayName("测试获取公开知识库列表（按引用次数）")
    void testListPublicKnowledgeBasesByUsage() {
        // Arrange
        List<KnowledgeBaseEntity> entities = List.of(
                createPublicKB(1L, 100L, "Java面试"),
                createPublicKB(2L, 101L, "Python面试")
        );
        List<KnowledgeBaseListItemDTO> dtos = List.of(
                createDTO(1L, "Java面试", true, 10),
                createDTO(2L, "Python面试", true, 5)
        );

        when(knowledgeBaseRepository.findByIsPublicTrueOrderByUsageCountDesc())
                .thenReturn(entities);
        when(knowledgeBaseMapper.toListItemDTOList(entities))
                .thenReturn(dtos);

        // Act
        List<KnowledgeBaseListItemDTO> result = shareService.listPublicKnowledgeBases("usage");

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("测试获取公开知识库列表（按时间）")
    void testListPublicKnowledgeBasesByTime() {
        // Arrange
        List<KnowledgeBaseEntity> entities = List.of(
                createPublicKB(1L, 100L, "Java面试")
        );
        List<KnowledgeBaseListItemDTO> dtos = List.of(
                createDTO(1L, "Java面试", true, 10)
        );

        when(knowledgeBaseRepository.findByIsPublicTrueOrderByUploadedAtDesc())
                .thenReturn(entities);
        when(knowledgeBaseMapper.toListItemDTOList(entities))
                .thenReturn(dtos);

        // Act
        List<KnowledgeBaseListItemDTO> result = shareService.listPublicKnowledgeBases("time");

        // Assert
        assertEquals(1, result.size());
        verify(knowledgeBaseRepository).findByIsPublicTrueOrderByUploadedAtDesc();
    }

    @Test
    @DisplayName("测试搜索公开知识库")
    void testSearchPublicKnowledgeBases() {
        // Arrange
        List<KnowledgeBaseEntity> entities = List.of(
                createPublicKB(1L, 100L, "Java面试题")
        );
        List<KnowledgeBaseListItemDTO> dtos = List.of(
                createDTO(1L, "Java面试题", true, 10)
        );

        when(knowledgeBaseRepository.searchPublicByKeyword("Java"))
                .thenReturn(entities);
        when(knowledgeBaseMapper.toListItemDTOList(entities))
                .thenReturn(dtos);

        // Act
        List<KnowledgeBaseListItemDTO> result = shareService.searchPublicKnowledgeBases("Java");

        // Assert
        assertEquals(1, result.size());
        assertEquals("Java面试题", result.get(0).name());
    }

    @Test
    @DisplayName("测试引用公开知识库")
    void testReferenceKnowledgeBase() {
        // Arrange
        Long userId = 2L;
        Long ownerUserId = 1L;
        Long kbId = 100L;
        KnowledgeBaseEntity entity = createPublicKB(kbId, ownerUserId, "公开知识库");
        entity.setUsageCount(5);
        KnowledgeBaseListItemDTO dto = createDTO(kbId, "公开知识库", true, 6);

        when(knowledgeBaseRepository.findByIdAndIsPublicTrue(kbId))
                .thenReturn(Optional.of(entity));
        when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class)))
                .thenReturn(entity);
        when(knowledgeBaseMapper.toListItemDTO(any(KnowledgeBaseEntity.class)))
                .thenReturn(dto);

        // Act
        KnowledgeBaseListItemDTO result = shareService.referenceKnowledgeBase(userId, kbId);

        // Assert
        assertNotNull(result);
        assertEquals(6, result.usageCount());
        verify(knowledgeBaseRepository).save(any(KnowledgeBaseEntity.class));
        // 奖励分享者积分
        verify(pointsService).shareKnowledgeBase(ownerUserId, kbId);
    }

    @Test
    @DisplayName("测试引用自己的公开知识库不奖励积分")
    void testReferenceOwnKnowledgeBase() {
        // Arrange
        Long userId = 1L;
        Long kbId = 100L;
        KnowledgeBaseEntity entity = createPublicKB(kbId, userId, "我的知识库");
        entity.setUsageCount(5);
        KnowledgeBaseListItemDTO dto = createDTO(kbId, "我的知识库", true, 6);

        when(knowledgeBaseRepository.findByIdAndIsPublicTrue(kbId))
                .thenReturn(Optional.of(entity));
        when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class)))
                .thenReturn(entity);
        when(knowledgeBaseMapper.toListItemDTO(any(KnowledgeBaseEntity.class)))
                .thenReturn(dto);

        // Act
        KnowledgeBaseListItemDTO result = shareService.referenceKnowledgeBase(userId, kbId);

        // Assert
        assertNotNull(result);
        assertEquals(6, result.usageCount());
        // 不奖励积分给自己
        verify(pointsService, never()).shareKnowledgeBase(anyLong(), anyLong());
    }

    @Test
    @DisplayName("测试引用不存在的公开知识库")
    void testReferenceNonPublicKnowledgeBase() {
        // Arrange
        Long userId = 1L;
        Long kbId = 999L;

        when(knowledgeBaseRepository.findByIdAndIsPublicTrue(kbId))
                .thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> shareService.referenceKnowledgeBase(userId, kbId));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("测试设置不存在的知识库公开/私有")
    void testSetVisibilityNotFound() {
        // Arrange
        Long userId = 1L;
        Long kbId = 999L;

        when(knowledgeBaseRepository.findByIdAndUserId(kbId, userId))
                .thenReturn(Optional.empty());

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> shareService.setVisibility(userId, kbId, true));

        assertEquals(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND.getCode(), exception.getCode());
    }

    // 辅助方法
    private KnowledgeBaseEntity createPrivateKB(Long id, Long userId, String name) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName(name);
        entity.setOriginalFilename(name + ".pdf");
        entity.setFileHash("hash" + id);
        entity.setIsPublic(false);
        entity.setUsageCount(0);
        entity.setUploadedAt(LocalDateTime.now());
        entity.setVectorStatus(VectorStatus.COMPLETED);
        return entity;
    }

    private KnowledgeBaseEntity createPublicKB(Long id, Long userId, String name) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName(name);
        entity.setOriginalFilename(name + ".pdf");
        entity.setFileHash("hash" + id);
        entity.setIsPublic(true);
        entity.setUsageCount(0);
        entity.setUploadedAt(LocalDateTime.now());
        entity.setVectorStatus(VectorStatus.COMPLETED);
        return entity;
    }

    private KnowledgeBaseListItemDTO createDTO(Long id, String name, boolean isPublic) {
        return createDTO(id, name, isPublic, 0);
    }

    private KnowledgeBaseListItemDTO createDTO(Long id, String name, boolean isPublic, int usageCount) {
        return new KnowledgeBaseListItemDTO(
                id,
                name,
                null,
                name + ".pdf",
                1024L,
                "application/pdf",
                LocalDateTime.now(),
                LocalDateTime.now(),
                0,
                0,
                VectorStatus.COMPLETED,
                null,
                0,
                isPublic,
                usageCount
        );
    }
}

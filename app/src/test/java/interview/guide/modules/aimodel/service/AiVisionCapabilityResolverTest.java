package interview.guide.modules.aimodel.service;

import interview.guide.modules.aimodel.enums.AiModelConfigType;
import interview.guide.modules.aimodel.model.AiModelConfigEntity;
import interview.guide.modules.aimodel.repository.AiModelConfigRepository;
import interview.guide.modules.aimodel.service.AiVisionCapabilityResolver.VisionCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 视觉能力解析器单元测试：CHAT→SMALL_CHAT 候选链与视觉优先判断
 */
@DisplayName("视觉能力解析器测试")
@ExtendWith(MockitoExtension.class)
class AiVisionCapabilityResolverTest {

    @Mock
    private AiModelConfigRepository configRepository;

    @Mock
    private AiModelRegistry registry;

    private AiVisionCapabilityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AiVisionCapabilityResolver(configRepository, registry);
    }

    private AiModelConfigEntity config(long id, boolean supportsVision, boolean visionPriority) {
        return AiModelConfigEntity.builder()
                .id(id)
                .provider("minimax")
                .displayName("test-" + id)
                .baseUrl("https://example.com/")
                .apiKey("key")
                .modelName("model-" + id)
                .supportsVision(supportsVision)
                .visionPriority(visionPriority)
                .build();
    }

    @Test
    @DisplayName("主模型支持视觉：仅返回 CHAT 候选，视觉优先随主模型配置")
    void chatSupportsVision() {
        AiModelConfigEntity chat = config(1L, true, false);
        when(registry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(1L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(chat));
        lenient().when(registry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(null);

        List<VisionCandidate> candidates = resolver.resolveVisionCandidates();

        assertEquals(1, candidates.size());
        assertEquals(AiModelConfigType.CHAT, candidates.get(0).role());
        assertEquals(1L, candidates.get(0).config().getId());
        assertFalse(AiVisionCapabilityResolver.isVisionPriority(candidates));
    }

    @Test
    @DisplayName("主模型不支持视觉：退化取 SMALL_CHAT 候选")
    void chatNotVisionFallsBackToSmallChat() {
        AiModelConfigEntity chat = config(1L, false, false);
        AiModelConfigEntity small = config(2L, true, true);
        when(registry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(1L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(registry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(2L);
        when(configRepository.findById(2L)).thenReturn(Optional.of(small));

        List<VisionCandidate> candidates = resolver.resolveVisionCandidates();

        assertEquals(1, candidates.size());
        assertEquals(AiModelConfigType.SMALL_CHAT, candidates.get(0).role());
        // 视觉优先按退化后实际使用的候选（小模型）配置判断
        assertTrue(AiVisionCapabilityResolver.isVisionPriority(candidates));
    }

    @Test
    @DisplayName("主模型支持视觉时视觉优先以主模型为准，小模型配置不参与")
    void visionPriorityFollowsFirstCandidate() {
        AiModelConfigEntity chat = config(1L, true, false);
        AiModelConfigEntity small = config(2L, true, true);
        when(registry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(1L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(chat));
        when(registry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(2L);
        when(configRepository.findById(2L)).thenReturn(Optional.of(small));

        List<VisionCandidate> candidates = resolver.resolveVisionCandidates();

        assertEquals(2, candidates.size());
        assertFalse(AiVisionCapabilityResolver.isVisionPriority(candidates));
    }

    @Test
    @DisplayName("两槽都不支持视觉：候选为空")
    void noneSupportsVision() {
        when(registry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(1L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config(1L, false, false)));
        when(registry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(2L);
        when(configRepository.findById(2L)).thenReturn(Optional.of(config(2L, false, false)));

        assertTrue(resolver.resolveVisionCandidates().isEmpty());
    }

    @Test
    @DisplayName("槽位未指派（SMALL_CHAT 空）：跳过该槽位")
    void smallChatSlotEmpty() {
        when(registry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(1L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(config(1L, true, false)));
        when(registry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(null);

        assertEquals(1, resolver.resolveVisionCandidates().size());
    }

    @Test
    @DisplayName("同一凭证被两个槽位引用：去重只计一次")
    void sameConfigReferencedByBothRoles() {
        AiModelConfigEntity shared = config(1L, true, false);
        when(registry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(1L);
        when(registry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(1L);
        when(configRepository.findById(1L)).thenReturn(Optional.of(shared));

        assertEquals(1, resolver.resolveVisionCandidates().size());
    }
}

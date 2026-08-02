package interview.guide.modules.aimodel.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.mapper.AiModelConfigMapper;
import interview.guide.modules.admin.service.AuditLogService;
import interview.guide.modules.aimodel.enums.AiModelConfigType;
import interview.guide.modules.aimodel.model.AiModelActiveRoleEntity;
import interview.guide.modules.aimodel.model.AiModelConfigDTO;
import interview.guide.modules.aimodel.model.AiModelConfigEntity;
import interview.guide.modules.aimodel.model.AiModelConfigRequest;
import interview.guide.modules.aimodel.repository.AiModelActiveRoleRepository;
import interview.guide.modules.aimodel.repository.AiModelConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiModelConfigService 单元测试（角色指派模型）
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>create：纯凭证落库；apiKey 为空拒绝</li>
 *   <li>update：apiKey 空 / 脱敏占位保留旧明文，真实值覆盖；被引用时触发热生效</li>
 *   <li>assignRole：CHAT+null 拒绝；configId 不存在拒绝；合法指派 upsert + reload</li>
 *   <li>disableRole：SMALL_CHAT 退化为空槽 + reload；CHAT 拒绝</li>
 *   <li>delete：被任一角色引用拒绝（AI_MODEL_CONFIG_IN_USE）；未引用才删除</li>
 * </ul>
 *
 * <p>纯 Mockito mock repository / activeRoleRepository / mapper / registry / auditLogService，不依赖真实 DB 与 Spring 事务上下文。
 * 无事务上下文时 {@code reloadAfterCommit} 回退为立即调用 {@code registry.reload}。
 */
@DisplayName("AI 模型配置服务测试")
class AiModelConfigServiceTest {

    @Mock
    private AiModelConfigRepository aiModelConfigRepository;

    @Mock
    private AiModelActiveRoleRepository aiModelActiveRoleRepository;

    @Mock
    private AiModelConfigMapper aiModelConfigMapper;

    @Mock
    private AiModelRegistry aiModelRegistry;

    @Mock
    private AuditLogService auditLogService;

    private AiModelConfigService aiModelConfigService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aiModelConfigService = new AiModelConfigService(
                aiModelConfigRepository, aiModelActiveRoleRepository, aiModelConfigMapper,
                aiModelRegistry, auditLogService);
    }

    // ========== create ==========

    @Nested
    @DisplayName("新建配置 create")
    class CreateTests {

        @Test
        @DisplayName("正常新建：纯凭证落库，不触发 reload（默认不被引用）")
        void testCreate_WhenValid_PersistAndNoReload() {
            // Given
            AiModelConfigRequest request = baseRequest("sk-real-key").build();
            AiModelConfigEntity entity = baseEntity("sk-real-key").build();
            when(aiModelConfigMapper.toEntity(request)).thenReturn(entity);
            when(aiModelConfigRepository.save(entity)).thenReturn(entity);
            when(aiModelConfigMapper.toDTO(entity)).thenReturn(AiModelConfigDTO.builder().id(1L).build());

            // When
            aiModelConfigService.create(request, 1L, "admin");

            // Then: 落库，不绑角色，不 reload
            verify(aiModelConfigRepository).save(entity);
            // baseUrl 保存前被规范化为完整 URL（域名根 → 补 /v1）
            assertEquals("https://api.minimaxi.com/v1", entity.getBaseUrl());
            verify(aiModelRegistry, never()).reload(any());
            verify(aiModelActiveRoleRepository, never()).save(any());
        }

        @Test
        @DisplayName("useFullUrl=true：完整 URL 原样入库（已含 /v4 不补 /v1）")
        void testCreate_WhenUseFullUrl_KeepAsIs() {
            AiModelConfigRequest request = baseRequest("sk-real-key")
                    .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                    .useFullUrl(true)
                    .build();
            AiModelConfigEntity entity = baseEntity("sk-real-key")
                    .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                    .build();
            when(aiModelConfigMapper.toEntity(request)).thenReturn(entity);
            when(aiModelConfigRepository.save(entity)).thenReturn(entity);
            when(aiModelConfigMapper.toDTO(entity)).thenReturn(AiModelConfigDTO.builder().id(1L).build());

            aiModelConfigService.create(request, 1L, "admin");

            // useFullUrl=true：原样入库，不补 /v1
            assertEquals("https://open.bigmodel.cn/api/paas/v4", entity.getBaseUrl());
            verify(aiModelConfigRepository).save(entity);
        }

        @Test
        @DisplayName("API Key 为空：拒绝新建")
        void testCreate_WhenApiKeyBlank_Throw() {
            AiModelConfigRequest request = baseRequest("").build();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiModelConfigService.create(request, 1L, "admin"));
            assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            verify(aiModelConfigRepository, never()).save(any());
        }
    }

    // ========== update ==========

    @Nested
    @DisplayName("更新配置 update")
    class UpdateTests {

        @Test
        @DisplayName("apiKey 为空：保留旧明文；未被引用不 reload")
        void testUpdate_WhenApiKeyBlank_KeepOldAndNoReload() {
            AiModelConfigEntity existing = baseEntity("old-key").id(1L).build();
            when(aiModelConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(aiModelConfigRepository.save(existing)).thenReturn(existing);
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(99L);
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(99L);
            when(aiModelConfigMapper.toDTO(existing)).thenReturn(AiModelConfigDTO.builder().build());
            AiModelConfigRequest request = baseRequest("").build();

            aiModelConfigService.update(1L, request, 1L, "admin");

            assertEquals("old-key", existing.getApiKey());
            verify(aiModelRegistry, never()).reload(any());
        }

        @Test
        @DisplayName("apiKey 为脱敏占位（******）：保留旧明文")
        void testUpdate_WhenApiKeyMasked_KeepOld() {
            AiModelConfigEntity existing = baseEntity("old-key").id(1L).build();
            when(aiModelConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(aiModelConfigRepository.save(existing)).thenReturn(existing);
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(null);
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(null);
            when(aiModelConfigMapper.toDTO(existing)).thenReturn(AiModelConfigDTO.builder().build());
            AiModelConfigRequest request = baseRequest("******").build();

            aiModelConfigService.update(1L, request, 1L, "admin");

            assertEquals("old-key", existing.getApiKey());
        }

        @Test
        @DisplayName("apiKey 为真实值：覆盖；被 CHAT 引用时触发 reload(CHAT)")
        void testUpdate_WhenApiKeyRealAndReferenced_OverwriteAndReload() {
            AiModelConfigEntity existing = baseEntity("old-key").id(1L).build();
            when(aiModelConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(aiModelConfigRepository.save(existing)).thenReturn(existing);
            // id=1 被 CHAT 引用
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(1L);
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(null);
            when(aiModelConfigMapper.toDTO(existing)).thenReturn(AiModelConfigDTO.builder().build());
            AiModelConfigRequest request = baseRequest("new-real-key").build();

            aiModelConfigService.update(1L, request, 1L, "admin");

            assertEquals("new-real-key", existing.getApiKey());
            // update 同样把 baseUrl 规范化为完整 URL（域名根 → 补 /v1）
            assertEquals("https://api.minimaxi.com/v1", existing.getBaseUrl());
            verify(aiModelRegistry).reload(AiModelConfigType.CHAT);
            verify(aiModelRegistry, never()).reload(AiModelConfigType.SMALL_CHAT);
        }
    }

    // ========== assignRole ==========

    @Nested
    @DisplayName("指派角色 assignRole")
    class AssignRoleTests {

        @Test
        @DisplayName("CHAT + configId=null：拒绝（主模型不能为空）")
        void testAssignRole_WhenChatNull_Throw() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiModelConfigService.assignRole(AiModelConfigType.CHAT, null, 1L, "admin"));
            assertEquals(ErrorCode.AI_MODEL_ROLE_CHAT_REQUIRED.getCode(), ex.getCode());
            verify(aiModelActiveRoleRepository, never()).save(any());
            verify(aiModelRegistry, never()).reload(any());
        }

        @Test
        @DisplayName("configId 不存在：拒绝")
        void testAssignRole_WhenConfigNotFound_Throw() {
            when(aiModelConfigRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiModelConfigService.assignRole(AiModelConfigType.CHAT, 99L, 1L, "admin"));
            assertEquals(ErrorCode.AI_MODEL_CONFIG_NOT_FOUND.getCode(), ex.getCode());
            verify(aiModelActiveRoleRepository, never()).save(any());
            verify(aiModelRegistry, never()).reload(any());
        }

        @Test
        @DisplayName("合法指派 CHAT：校验存在 → upsert 槽位 → reload(CHAT)")
        void testAssignRole_WhenValidChat_UpsertAndReload() {
            AiModelConfigEntity target = baseEntity("key").id(5L).build();
            when(aiModelConfigRepository.findById(5L)).thenReturn(Optional.of(target));
            AiModelActiveRoleEntity slot = new AiModelActiveRoleEntity();
            slot.setRole(AiModelConfigType.CHAT);
            when(aiModelActiveRoleRepository.findByRole(AiModelConfigType.CHAT))
                    .thenReturn(Optional.of(slot));
            when(aiModelActiveRoleRepository.save(slot)).thenReturn(slot);

            aiModelConfigService.assignRole(AiModelConfigType.CHAT, 5L, 1L, "admin");

            assertEquals(5L, slot.getConfigId());
            verify(aiModelActiveRoleRepository).save(slot);
            verify(aiModelRegistry).reload(AiModelConfigType.CHAT);
        }

        @Test
        @DisplayName("槽位记录不存在时：兜底新建并指派")
        void testAssignRole_WhenSlotMissing_CreateSlot() {
            AiModelConfigEntity target = baseEntity("key").id(7L).build();
            when(aiModelConfigRepository.findById(7L)).thenReturn(Optional.of(target));
            when(aiModelActiveRoleRepository.findByRole(AiModelConfigType.SMALL_CHAT))
                    .thenReturn(Optional.empty());
            when(aiModelActiveRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            aiModelConfigService.assignRole(AiModelConfigType.SMALL_CHAT, 7L, 1L, "admin");

            verify(aiModelActiveRoleRepository).save(any());
            verify(aiModelRegistry).reload(AiModelConfigType.SMALL_CHAT);
        }
    }

    // ========== disableRole ==========

    @Nested
    @DisplayName("禁用角色 disableRole")
    class DisableRoleTests {

        @Test
        @DisplayName("禁用 SMALL_CHAT：槽位置空 → reload(SMALL_CHAT)")
        void testDisableRole_WhenSmallChat_SetNullAndReload() {
            AiModelActiveRoleEntity slot = new AiModelActiveRoleEntity();
            slot.setRole(AiModelConfigType.SMALL_CHAT);
            slot.setConfigId(5L);
            when(aiModelActiveRoleRepository.findByRole(AiModelConfigType.SMALL_CHAT))
                    .thenReturn(Optional.of(slot));
            when(aiModelActiveRoleRepository.save(slot)).thenReturn(slot);

            aiModelConfigService.disableRole(AiModelConfigType.SMALL_CHAT, 1L, "admin");

            assertNull(slot.getConfigId());
            verify(aiModelActiveRoleRepository).save(slot);
            verify(aiModelRegistry).reload(AiModelConfigType.SMALL_CHAT);
        }

        @Test
        @DisplayName("禁用 CHAT：拒绝（主模型不能为空）")
        void testDisableRole_WhenChat_Throw() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiModelConfigService.disableRole(AiModelConfigType.CHAT, 1L, "admin"));
            assertEquals(ErrorCode.AI_MODEL_ROLE_CHAT_REQUIRED.getCode(), ex.getCode());
            verify(aiModelActiveRoleRepository, never()).save(any());
        }
    }

    // ========== delete ==========

    @Nested
    @DisplayName("删除配置 delete")
    class DeleteTests {

        @Test
        @DisplayName("被 CHAT 引用：拒绝（IN_USE）")
        void testDelete_WhenReferencedByChat_Throw() {
            AiModelConfigEntity entity = baseEntity("key").id(1L).build();
            when(aiModelConfigRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(1L);
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiModelConfigService.delete(1L, 1L, "admin"));
            assertEquals(ErrorCode.AI_MODEL_CONFIG_IN_USE.getCode(), ex.getCode());
            verify(aiModelConfigRepository, never()).delete(any());
        }

        @Test
        @DisplayName("被 SMALL_CHAT 引用：拒绝（IN_USE）")
        void testDelete_WhenReferencedBySmallChat_Throw() {
            AiModelConfigEntity entity = baseEntity("key").id(2L).build();
            when(aiModelConfigRepository.findById(2L)).thenReturn(Optional.of(entity));
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(null);
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiModelConfigService.delete(2L, 1L, "admin"));
            assertEquals(ErrorCode.AI_MODEL_CONFIG_IN_USE.getCode(), ex.getCode());
            verify(aiModelConfigRepository, never()).delete(any());
        }

        @Test
        @DisplayName("未被任何角色引用：允许删除")
        void testDelete_WhenNotReferenced_Allowed() {
            AiModelConfigEntity entity = baseEntity("key").id(3L).build();
            when(aiModelConfigRepository.findById(3L)).thenReturn(Optional.of(entity));
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT)).thenReturn(99L);
            when(aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT)).thenReturn(99L);

            aiModelConfigService.delete(3L, 1L, "admin");

            verify(aiModelConfigRepository).delete(entity);
            // 删的是未被引用的凭证，无需 reload
            verify(aiModelRegistry, never()).reload(any());
        }
    }

    // ========== 测试数据构造辅助 ==========

    private static AiModelConfigRequest.AiModelConfigRequestBuilder baseRequest(String apiKey) {
        return AiModelConfigRequest.builder()
                .provider("minimax")
                .displayName("主模型")
                .baseUrl("https://api.minimaxi.com/")
                .apiKey(apiKey)
                .modelName("MiniMax-M2.7");
    }

    private static AiModelConfigEntity.AiModelConfigEntityBuilder baseEntity(String apiKey) {
        return AiModelConfigEntity.builder()
                .provider("minimax")
                .displayName("主模型")
                .baseUrl("https://api.minimaxi.com/")
                .apiKey(apiKey)
                .modelName("MiniMax-M2.7")
                .temperature(0.2);
    }
}

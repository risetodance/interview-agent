package interview.guide.modules.aimodel.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.mapper.AiModelConfigMapper;
import interview.guide.modules.admin.service.AuditLogService;
import interview.guide.modules.aimodel.enums.AiModelConfigType;
import interview.guide.modules.aimodel.model.ActiveRoles;
import interview.guide.modules.aimodel.model.AiModelActiveRoleEntity;
import interview.guide.modules.aimodel.model.AiModelConfigDTO;
import interview.guide.modules.aimodel.model.AiModelConfigEntity;
import interview.guide.modules.aimodel.model.AiModelConfigListResponse;
import interview.guide.modules.aimodel.model.AiModelConfigRequest;
import interview.guide.modules.aimodel.repository.AiModelActiveRoleRepository;
import interview.guide.modules.aimodel.repository.AiModelConfigRepository;
import interview.guide.modules.aimodel.util.AiHttpClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 模型配置服务（角色指派模型）
 * <p>凭证 CRUD + 角色槽位指派 / 禁用 + 删除。写操作触发 {@link AiModelRegistry#reload} 热生效，并记录审计日志。
 * <p>api_key 明文落库；查询 DTO 不含 key；更新时 key 为空 / 脱敏占位则保留旧明文。
 * <p>核心语义：
 * <ul>
 *   <li>凭证与角色解耦：create / update 只动凭证，不绑角色。</li>
 *   <li>{@link #assignRole} 更新槽位指派；CHAT 不允许置空（拒绝），SMALL_CHAT 可置空（禁用退化主模型）。</li>
 *   <li>{@link #disableRole} = assignRole(role, null)，仅 SMALL_CHAT 允许。</li>
 *   <li>{@link #delete} 被任一角色引用时拒绝（IN_USE），需先取消指派。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigService {

    private final AiModelConfigRepository aiModelConfigRepository;
    private final AiModelActiveRoleRepository aiModelActiveRoleRepository;
    private final AiModelConfigMapper aiModelConfigMapper;
    private final AiModelRegistry aiModelRegistry;
    private final AuditLogService auditLogService;

    /** 默认温度 */
    private static final double DEFAULT_TEMPERATURE = 0.2;

    /**
     * 列表：全部凭证（按创建时间倒序）+ 当前角色指派映射
     */
    @Transactional(readOnly = true)
    public AiModelConfigListResponse list() {
        List<AiModelConfigDTO> configs = aiModelConfigRepository
                .findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(aiModelConfigMapper::toDTO)
                .toList();
        ActiveRoles activeRoles = new ActiveRoles(
                aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT),
                aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT));
        return AiModelConfigListResponse.builder()
                .configs(configs)
                .activeRoles(activeRoles)
                .build();
    }

    /**
     * 详情
     */
    @Transactional(readOnly = true)
    public AiModelConfigDTO get(Long id) {
        return aiModelConfigMapper.toDTO(getOrThrow(id));
    }

    /**
     * 新建：纯凭证落库，不绑角色（需在列表里显式指派）。
     */
    @Transactional
    public AiModelConfigDTO create(AiModelConfigRequest request, Long operatorId, String operatorUsername) {
        if (!StringUtils.hasText(request.getApiKey())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新建配置必须填写 API Key");
        }
        VisionFlags visionFlags = validatedVisionFlags(request);
        AiModelConfigEntity entity = aiModelConfigMapper.toEntity(request);
        // 保存前规范化 baseUrl 为完整 URL（含版本前缀），调用方据此只拼端点后缀
        entity.setBaseUrl(AiHttpClientFactory.normalizeForStorage(
                request.getBaseUrl(), Boolean.TRUE.equals(request.getUseFullUrl())));
        if (entity.getTemperature() == null) {
            entity.setTemperature(DEFAULT_TEMPERATURE);
        }
        entity.setSupportsVision(visionFlags.supportsVision());
        entity.setVisionPriority(visionFlags.visionPriority());
        AiModelConfigEntity saved = aiModelConfigRepository.save(entity);
        log.info("新建 AI 模型配置: id={}, model={}", saved.getId(), saved.getModelName());
        audit("AI_MODEL_CREATE", operatorId, operatorUsername, saved.getId(), "新建配置：" + saved.getDisplayName());
        return aiModelConfigMapper.toDTO(saved);
    }

    /**
     * 更新：apiKey 空 / 占位则保留旧明文，否则覆盖；若该凭证正被某角色引用，更新后触发热生效。
     */
    @Transactional
    public AiModelConfigDTO update(Long id, AiModelConfigRequest request, Long operatorId, String operatorUsername) {
        AiModelConfigEntity entity = getOrThrow(id);
        VisionFlags visionFlags = validatedVisionFlags(request);
        entity.setProvider(request.getProvider());
        entity.setDisplayName(request.getDisplayName());
        entity.setBaseUrl(AiHttpClientFactory.normalizeForStorage(
                request.getBaseUrl(), Boolean.TRUE.equals(request.getUseFullUrl())));
        entity.setModelName(request.getModelName());
        if (request.getTemperature() != null) {
            entity.setTemperature(request.getTemperature());
        }
        if (hasRealApiKey(request.getApiKey())) {
            entity.setApiKey(request.getApiKey().trim());
        }
        entity.setSupportsVision(visionFlags.supportsVision());
        entity.setVisionPriority(visionFlags.visionPriority());

        AiModelConfigEntity saved = aiModelConfigRepository.save(entity);
        reloadIfReferenced(saved.getId());
        log.info("更新 AI 模型配置: id={}, model={}", saved.getId(), saved.getModelName());
        audit("AI_MODEL_UPDATE", operatorId, operatorUsername, saved.getId(), "更新配置：" + saved.getDisplayName());
        return aiModelConfigMapper.toDTO(saved);
    }

    /**
     * 指派角色：更新 ai_model_active_role 槽位 → reloadAfterCommit。
     * <ul>
     *   <li>CHAT + configId=null：拒绝（AI_MODEL_ROLE_CHAT_REQUIRED，主模型不能为空）。</li>
     *   <li>configId 非空：校验该凭证存在，不存在抛 AI_MODEL_CONFIG_NOT_FOUND。</li>
     *   <li>configId=null（仅 SMALL_CHAT 合法）：禁用该角色，运行时退化主模型。</li>
     * </ul>
     */
    @Transactional
    public void assignRole(AiModelConfigType role, Long configId, Long operatorId, String operatorUsername) {
        if (role == AiModelConfigType.CHAT && configId == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_ROLE_CHAT_REQUIRED);
        }
        String displayName = null;
        if (configId != null) {
            AiModelConfigEntity target = aiModelConfigRepository.findById(configId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AI_MODEL_CONFIG_NOT_FOUND));
            displayName = target.getDisplayName();
        }
        upsertSlot(role, configId);
        reloadAfterCommit(role);
        log.info("指派 AI 模型角色: role={}, configId={}", role, configId);
        String detail = configId == null
                ? "禁用 " + role + " 角色（退化主模型）"
                : "指派 " + role + " 角色 → " + displayName;
        audit("AI_MODEL_ASSIGN_ROLE", operatorId, operatorUsername, configId, detail);
    }

    /**
     * 禁用角色：= assignRole(role, null)。仅 SMALL_CHAT 允许（CHAT 会被 assignRole 拒绝）。
     */
    @Transactional
    public void disableRole(AiModelConfigType role, Long operatorId, String operatorUsername) {
        assignRole(role, null, operatorId, operatorUsername);
    }

    /**
     * 删除：被任一角色引用时拒绝（AI_MODEL_CONFIG_IN_USE），需先取消指派或改指派；未被引用才允许删除。
     * <p>删除未被引用的凭证不影响任何槽位指派，无需 reload。
     */
    @Transactional
    public void delete(Long id, Long operatorId, String operatorUsername) {
        AiModelConfigEntity entity = getOrThrow(id);
        if (isReferenced(id)) {
            throw new BusinessException(ErrorCode.AI_MODEL_CONFIG_IN_USE);
        }
        String name = entity.getDisplayName();
        aiModelConfigRepository.delete(entity);
        log.info("删除 AI 模型配置: id={}", id);
        audit("AI_MODEL_DELETE", operatorId, operatorUsername, id, "删除配置：" + name);
    }

    // ========== 内部工具 ==========

    /**
     * 归一化并校验视觉能力两字段：null 视为 false；
     * 「视觉优先」依赖「支持视觉」能力，不支持视觉时不允许开启视觉优先。
     */
    private VisionFlags validatedVisionFlags(AiModelConfigRequest request) {
        boolean supportsVision = Boolean.TRUE.equals(request.getSupportsVision());
        boolean visionPriority = Boolean.TRUE.equals(request.getVisionPriority());
        if (visionPriority && !supportsVision) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视觉优先依赖「支持视觉」能力，请先勾选支持视觉");
        }
        return new VisionFlags(supportsVision, visionPriority);
    }

    /** 视觉能力字段归一化结果（两列 NOT NULL，null 统一落 false） */
    private record VisionFlags(boolean supportsVision, boolean visionPriority) {}

    /** 槽位 upsert：存在则更新 configId，不存在则新建（初始化数据保证两行存在，此处兜底） */
    private AiModelActiveRoleEntity upsertSlot(AiModelConfigType role, Long configId) {
        AiModelActiveRoleEntity slot = aiModelActiveRoleRepository.findByRole(role)
                .orElseGet(() -> {
                    AiModelActiveRoleEntity e = new AiModelActiveRoleEntity();
                    e.setRole(role);
                    return e;
                });
        slot.setConfigId(configId);
        return aiModelActiveRoleRepository.save(slot);
    }

    /** 该凭证是否被任一角色引用 */
    private boolean isReferenced(Long configId) {
        return configId.equals(aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT))
                || configId.equals(aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT));
    }

    /** 该凭证被引用时，按引用它的角色分别触发 reload */
    private void reloadIfReferenced(Long configId) {
        if (configId.equals(aiModelRegistry.getActiveConfigId(AiModelConfigType.CHAT))) {
            reloadAfterCommit(AiModelConfigType.CHAT);
        }
        if (configId.equals(aiModelRegistry.getActiveConfigId(AiModelConfigType.SMALL_CHAT))) {
            reloadAfterCommit(AiModelConfigType.SMALL_CHAT);
        }
    }

    /**
     * 在当前事务提交后（afterCommit）触发 reload，避免把重建 HttpClient + ChatModel 的重操作关在
     * DB 事务内拉长持锁时间。
     * <p>语义保证：DB 写失败（事务回滚）时 afterCommit 不执行，不会 reload；
     * reload 自身抛异常被吞掉仅记日志，不影响已提交的 DB 结果（DB 是 source of truth，内存可在下次 reload 刷新）。
     * 无事务上下文时（如单元测试直接调用）回退为立即 reload。
     */
    private void reloadAfterCommit(AiModelConfigType configType) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        aiModelRegistry.reload(configType);
                    } catch (Exception e) {
                        log.error("事务提交后 reload AI 模型失败（DB 已提交，内存需关注）: type={}", configType, e);
                    }
                }
            });
        } else {
            aiModelRegistry.reload(configType);
        }
    }

    private AiModelConfigEntity getOrThrow(Long id) {
        return aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_MODEL_CONFIG_NOT_FOUND));
    }

    /** apiKey 为空 / 脱敏占位 → 视为不修改 */
    private boolean hasRealApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        String trimmed = apiKey.trim();
        return !trimmed.startsWith("****") && !"******".equals(trimmed);
    }

    /** 记录审计日志（异步，失败仅告警不中断主流程） */
    private void audit(String operation, Long operatorId, String operatorUsername, Long targetId, String details) {
        try {
            auditLogService.logOperation(operation, operatorId, operatorUsername,
                    "AI_MODEL", targetId, details, null);
        } catch (Exception e) {
            log.error("审计日志记录失败: op={}, err={}", operation, e.getMessage(), e);
        }
    }
}

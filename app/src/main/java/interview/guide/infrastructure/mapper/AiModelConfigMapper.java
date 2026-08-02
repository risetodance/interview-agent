package interview.guide.infrastructure.mapper;

import interview.guide.modules.aimodel.model.AiModelConfigDTO;
import interview.guide.modules.aimodel.model.AiModelConfigEntity;
import interview.guide.modules.aimodel.model.AiModelConfigRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * AI 模型配置对象映射器（MapStruct）
 * <p>Entity → DTO 时，DTO 无 apiKey 字段，MapStruct 自动忽略，天然实现对外脱敏。
 * <p>角色指派重构后：DTO / Request 均无 configType / isDefault / enabled，
 * 凭证字段同名同构直接映射，无需额外 ignore。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AiModelConfigMapper {

    /**
     * Entity → DTO（不含 apiKey，对外脱敏）
     */
    AiModelConfigDTO toDTO(AiModelConfigEntity entity);

    /**
     * Request → 新建 Entity（apiKey 明文映射；时间戳 / 测试结果由 service 层处理）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lastTestAt", ignore = true)
    @Mapping(target = "lastTestOk", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AiModelConfigEntity toEntity(AiModelConfigRequest request);
}

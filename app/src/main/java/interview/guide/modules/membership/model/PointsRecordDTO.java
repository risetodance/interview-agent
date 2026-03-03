package interview.guide.modules.membership.model;

import interview.guide.modules.membership.enums.PointsType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 积分记录 DTO
 */
@Getter
@Builder
public class PointsRecordDTO {

    /**
     * 记录ID
     */
    private final Long id;

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 积分变化数量（正数增加，负数减少）
     */
    private final Integer points;

    /**
     * 积分类型
     */
    private final PointsType type;

    /**
     * 描述
     */
    private final String description;

    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;
}

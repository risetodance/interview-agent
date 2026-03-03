package interview.guide.modules.membership.model;

import interview.guide.modules.membership.enums.PointsType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 积分记录实体
 */
@Entity
@Table(name = "points_records", indexes = {
    @Index(name = "idx_points_record_user_id", columnList = "userId"),
    @Index(name = "idx_points_record_created_at", columnList = "createdAt"),
    @Index(name = "idx_points_record_user_type_business", columnList = "userId, type, businessId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointsRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 积分数量（正数增加，负数扣减）
     */
    @Column(nullable = false)
    private Integer points;

    /**
     * 积分类型
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointsType type;

    /**
     * 描述
     */
    @Column(length = 200)
    private String description;

    /**
     * 业务关联ID（如知识库ID、面试ID等），用于防止重复领取
     */
    @Column(name = "business_id")
    private Long businessId;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

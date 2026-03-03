package interview.guide.modules.question.model;

import interview.guide.modules.question.enums.QuestionBankType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 题库实体
 */
@Entity
@Table(name = "question_banks", indexes = {
    @Index(name = "idx_question_bank_user", columnList = "userId"),
    @Index(name = "idx_question_bank_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionBankEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 题库名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 题库描述
     */
    @Column(length = 500)
    private String description;

    /**
     * 题库类型：SYSTEM（系统预置）或 USER（用户自定义）
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionBankType type;

    /**
     * 创建用户ID（SYSTEM 类型为 null）
     */
    @Column(name = "userId")
    private Long userId;

    /**
     * 题目数量
     */
    @Column(name = "question_count")
    @Builder.Default
    private Integer questionCount = 0;

    /**
     * 创建时间
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

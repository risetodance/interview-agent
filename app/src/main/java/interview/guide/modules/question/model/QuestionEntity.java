package interview.guide.modules.question.model;

import interview.guide.modules.question.enums.QuestionDifficulty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 题目实体
 */
@Entity
@Table(name = "questions", indexes = {
    @Index(name = "idx_question_bank", columnList = "questionBankId"),
    @Index(name = "idx_question_difficulty", columnList = "difficulty")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属题库ID
     */
    @Column(name = "question_bank_id", nullable = false)
    private Long questionBankId;

    /**
     * 题目内容
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 题目答案
     */
    @Column(columnDefinition = "TEXT")
    private String answer;

    /**
     * 题目难度：EASY / MEDIUM / HARD
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionDifficulty difficulty;

    /**
     * 题目标签（JSON 数组）
     */
    @Column(columnDefinition = "TEXT")
    private String tags;

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

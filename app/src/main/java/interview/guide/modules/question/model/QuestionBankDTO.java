package interview.guide.modules.question.model;

import interview.guide.modules.question.enums.QuestionBankType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 题库 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionBankDTO {

    private Long id;

    /**
     * 题库名称
     */
    private String name;

    /**
     * 题库描述
     */
    private String description;

    /**
     * 题库类型
     */
    private QuestionBankType type;

    /**
     * 创建用户ID
     */
    private Long userId;

    /**
     * 题目数量
     */
    private Integer questionCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

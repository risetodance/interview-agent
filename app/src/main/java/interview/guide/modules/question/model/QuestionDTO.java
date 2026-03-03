package interview.guide.modules.question.model;

import interview.guide.modules.question.enums.QuestionDifficulty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionDTO {

    private Long id;

    /**
     * 所属题库ID
     */
    private Long questionBankId;

    /**
     * 题目内容
     */
    private String content;

    /**
     * 题目答案
     */
    private String answer;

    /**
     * 题目难度
     */
    private QuestionDifficulty difficulty;

    /**
     * 题目标签列表
     */
    private List<String> tags;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

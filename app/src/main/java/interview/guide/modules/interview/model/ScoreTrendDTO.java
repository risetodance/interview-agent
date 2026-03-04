package interview.guide.modules.interview.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 评分趋势数据
 */
public record ScoreTrendDTO(
    List<DailyScore> dailyScores,     // 每日评分趋势
    ScoreStatistics statistics       // 统计信息
) {
    /**
     * 每日评分
     */
    public record DailyScore(
        LocalDate date,               // 日期
        double averageScore,          // 平均分
        int interviewCount            // 面试次数
    ) {}

    /**
     * 评分统计信息
     */
    public record ScoreStatistics(
        double averageScore,          // 总平均分
        int highestScore,             // 最高分
        int lowestScore,              // 最低分
        int totalInterviews           // 总面试次数
    ) {}
}

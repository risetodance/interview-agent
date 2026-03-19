package interview.guide.modules.interview.model;

/**
 * 分类得分 DTO
 * 用于能力画像中各类别的得分统计
 */
public class CategoryScoreDTO {

    private String category;
    private Integer totalScore;
    private Integer count;
    private Integer avgScore;

    public CategoryScoreDTO() {
    }

    public CategoryScoreDTO(String category, Integer totalScore, Integer count, Integer avgScore) {
        this.category = category;
        this.totalScore = totalScore;
        this.count = count;
        this.avgScore = avgScore;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getAvgScore() {
        return avgScore;
    }

    public void setAvgScore(Integer avgScore) {
        this.avgScore = avgScore;
    }
}

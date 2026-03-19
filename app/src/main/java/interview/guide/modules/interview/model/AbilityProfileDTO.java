package interview.guide.modules.interview.model;

import java.util.List;
import java.util.Map;

/**
 * 能力画像 DTO
 * 返回用户在各知识领域的得分情况
 */
public class AbilityProfileDTO {

    private Map<String, CategoryScoreDTO> categoryScores;
    private Integer overallScore;
    private List<String> strengths;
    private List<String> weaknesses;

    public AbilityProfileDTO() {
    }

    public AbilityProfileDTO(Map<String, CategoryScoreDTO> categoryScores, Integer overallScore,
                             List<String> strengths, List<String> weaknesses) {
        this.categoryScores = categoryScores;
        this.overallScore = overallScore;
        this.strengths = strengths;
        this.weaknesses = weaknesses;
    }

    public Map<String, CategoryScoreDTO> getCategoryScores() {
        return categoryScores;
    }

    public void setCategoryScores(Map<String, CategoryScoreDTO> categoryScores) {
        this.categoryScores = categoryScores;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getWeaknesses() {
        return weaknesses;
    }

    public void setWeaknesses(List<String> weaknesses) {
        this.weaknesses = weaknesses;
    }
}

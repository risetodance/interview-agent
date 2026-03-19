package interview.guide.modules.interview.model;

/**
 * 当前问题 DTO
 * 用于返回当前面试问题及其相关信息
 */
public class CurrentQuestionDTO {

    private Integer questionIndex;
    private String question;
    private String category;
    private String difficulty;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String referenceContext;

    public CurrentQuestionDTO() {
    }

    public CurrentQuestionDTO(Integer questionIndex, String question, String category,
                               String difficulty, Long knowledgeBaseId, String knowledgeBaseName,
                               String referenceContext) {
        this.questionIndex = questionIndex;
        this.question = question;
        this.category = category;
        this.difficulty = difficulty;
        this.knowledgeBaseId = knowledgeBaseId;
        this.knowledgeBaseName = knowledgeBaseName;
        this.referenceContext = referenceContext;
    }

    public Integer getQuestionIndex() {
        return questionIndex;
    }

    public void setQuestionIndex(Integer questionIndex) {
        this.questionIndex = questionIndex;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getKnowledgeBaseName() {
        return knowledgeBaseName;
    }

    public void setKnowledgeBaseName(String knowledgeBaseName) {
        this.knowledgeBaseName = knowledgeBaseName;
    }

    public String getReferenceContext() {
        return referenceContext;
    }

    public void setReferenceContext(String referenceContext) {
        this.referenceContext = referenceContext;
    }
}

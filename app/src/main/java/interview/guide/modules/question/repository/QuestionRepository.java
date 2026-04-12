package interview.guide.modules.question.repository;

import interview.guide.modules.interview.service.DifficultyAdjustmentService.Difficulty;
import interview.guide.modules.question.model.QuestionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 题目 Repository
 */
@Repository
public interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {

    /**
     * 查询指定题库下的所有题目
     */
    List<QuestionEntity> findByQuestionBankId(Long questionBankId);

    /**
     * 删除指定题库下的所有题目
     */
    void deleteByQuestionBankId(Long questionBankId);

    /**
     * 查询指定题库下指定难度的题目
     */
    List<QuestionEntity> findByQuestionBankIdAndDifficulty(Long questionBankId, Difficulty difficulty);

    /**
     * 分页查询 - 支持动态组合（难度 + 关键词搜索内容/答案/标签）
     */
    @Query("SELECT q FROM QuestionEntity q WHERE q.questionBankId = :bankId " +
           "AND (:difficulty IS NULL OR q.difficulty = :difficulty) " +
           "AND (:keyword IS NULL OR q.content LIKE %:keyword% OR q.answer LIKE %:keyword%)")
    Page<QuestionEntity> findByBankIdWithFilters(
            @Param("bankId") Long bankId,
            @Param("difficulty") Difficulty difficulty,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 统计指定题库的题目数量
     */
    long countByQuestionBankId(Long questionBankId);

    /**
     * 随机获取指定题库的题目
     */
    @Query(value = "SELECT * FROM questions WHERE question_bank_id = :bankId ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<QuestionEntity> findRandomQuestions(@Param("bankId") Long bankId, @Param("limit") int limit);

    /**
     * 随机获取指定题库列表的题目
     */
    @Query(value = "SELECT * FROM questions WHERE question_bank_id IN (:bankIds) ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<QuestionEntity> findRandomQuestionsByBankIds(@Param("bankIds") List<Long> bankIds, @Param("limit") int limit);

    /**
     * 全文搜索题目（使用 PostgreSQL 全文检索）
     * 按相关性排序返回结果
     */
    @Query(value = """
        SELECT *, ts_rank(content_tsv, plainto_tsquery('simple', :keywords)) AS rank
        FROM questions
        WHERE question_bank_id IN (:bankIds)
          AND content_tsv @@ plainto_tsquery('simple', :keywords)
        ORDER BY rank DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<QuestionEntity> fullTextSearch(@Param("bankIds") List<Long> bankIds, @Param("keywords") String keywords, @Param("limit") int limit);

    /**
     * 全文搜索题目（使用 PostgreSQL 全文检索，支持难度过滤）
     * 按相关性排序返回结果
     */
    @Query(value = """
        SELECT *, ts_rank(content_tsv, plainto_tsquery('simple', :keywords)) AS rank
        FROM questions
        WHERE question_bank_id IN (:bankIds)
          AND content_tsv @@ plainto_tsquery('simple', :keywords)
          AND (:difficulty IS NULL OR difficulty = :difficulty)
        ORDER BY rank DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<QuestionEntity> fullTextSearchWithDifficulty(
            @Param("bankIds") List<Long> bankIds,
            @Param("keywords") String keywords,
            @Param("difficulty") String difficulty,
            @Param("limit") int limit);
}

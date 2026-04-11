package interview.guide.modules.question.repository;

import interview.guide.modules.question.enums.QuestionDifficulty;
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
     * 分页查询指定题库下的所有题目
     */
    Page<QuestionEntity> findByQuestionBankId(Long questionBankId, Pageable pageable);

    /**
     * 查询指定题库下指定难度的题目
     */
    List<QuestionEntity> findByQuestionBankIdAndDifficulty(Long questionBankId, QuestionDifficulty difficulty);

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
     * 根据题库ID列表获取题目
     */
    @Query("SELECT q FROM QuestionEntity q WHERE q.questionBankId IN :bankIds")
    List<QuestionEntity> findByQuestionBankIdIn(@Param("bankIds") List<Long> bankIds);

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
}

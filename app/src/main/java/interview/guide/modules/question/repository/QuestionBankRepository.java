package interview.guide.modules.question.repository;

import interview.guide.modules.question.enums.QuestionBankType;
import interview.guide.modules.question.model.QuestionBankEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 题库 Repository
 */
@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBankEntity, Long> {

    /**
     * 查询所有系统预置题库
     */
    List<QuestionBankEntity> findByType(QuestionBankType type);

    /**
     * 查询指定用户的题库（包括系统预置题库）
     */
    @Query("SELECT qb FROM QuestionBankEntity qb WHERE qb.userId = :userId OR qb.type = 'SYSTEM' ORDER BY qb.type, qb.createdAt")
    List<QuestionBankEntity> findByUserIdOrSystem(@Param("userId") Long userId);

    /**
     * 查询用户自定义题库
     */
    List<QuestionBankEntity> findByUserId(Long userId);

    /**
     * 查询用户自定义题库（排除系统预置）
     */
    @Query("SELECT qb FROM QuestionBankEntity qb WHERE qb.userId = :userId AND qb.type = 'USER' ORDER BY qb.createdAt DESC")
    List<QuestionBankEntity> findUserBanks(@Param("userId") Long userId);

    /**
     * 检查题库名称是否已存在（用户题库）
     */
    boolean existsByNameAndUserIdAndType(String name, Long userId, QuestionBankType type);
}

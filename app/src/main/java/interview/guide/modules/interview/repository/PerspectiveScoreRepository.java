package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.PerspectiveScoreEntity;
import interview.guide.modules.interview.model.PerspectiveScoreEntity.PerspectiveScoreStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 各视角评分Repository
 */
@Repository
public interface PerspectiveScoreRepository extends JpaRepository<PerspectiveScoreEntity, Long> {

    /**
     * 根据会话ID查找所有评分
     */
    List<PerspectiveScoreEntity> findBySessionId(Long sessionId);

    /**
     * 根据会话ID查找所有评分（加载视角信息）
     */
    @Query("SELECT ps FROM PerspectiveScoreEntity ps JOIN FETCH ps.perspective WHERE ps.sessionId = :sessionId ORDER BY ps.questionIndex ASC")
    List<PerspectiveScoreEntity> findBySessionIdWithPerspective(@Param("sessionId") Long sessionId);

    /**
     * 根据会话ID、视角ID和问题索引查找评分
     */
    Optional<PerspectiveScoreEntity> findBySessionIdAndPerspectiveIdAndQuestionIndex(Long sessionId, Long perspectiveId, Integer questionIndex);

    /**
     * 根据会话ID和视角ID查找评分
     */
    List<PerspectiveScoreEntity> findBySessionIdAndPerspectiveId(Long sessionId, Long perspectiveId);

    /**
     * 根据会话ID和视角ID查找评分（加载视角信息）
     */
    @Query("SELECT ps FROM PerspectiveScoreEntity ps JOIN FETCH ps.perspective WHERE ps.sessionId = :sessionId AND ps.perspective.id = :perspectiveId ORDER BY ps.questionIndex ASC")
    List<PerspectiveScoreEntity> findBySessionIdAndPerspectiveIdWithPerspective(@Param("sessionId") Long sessionId, @Param("perspectiveId") Long perspectiveId);

    /**
     * 根据会话ID查找综合评分（questionIndex = -1）
     */
    @Query("SELECT ps FROM PerspectiveScoreEntity ps JOIN FETCH ps.perspective WHERE ps.sessionId = :sessionId AND ps.questionIndex = -1")
    List<PerspectiveScoreEntity> findComprehensiveScoresBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 根据会话ID和视角ID查找综合评分
     */
    @Query("SELECT ps FROM PerspectiveScoreEntity ps JOIN FETCH ps.perspective WHERE ps.sessionId = :sessionId AND ps.perspective.id = :perspectiveId AND ps.questionIndex = -1")
    Optional<PerspectiveScoreEntity> findComprehensiveScoreBySessionIdAndPerspectiveId(@Param("sessionId") Long sessionId, @Param("perspectiveId") Long perspectiveId);

    /**
     * 根据会话ID查找某个问题的所有视角评分
     */
    List<PerspectiveScoreEntity> findBySessionIdAndQuestionIndex(Long sessionId, Integer questionIndex);

    /**
     * 检查会话的所有视角评分是否都已完成
     */
    @Query("SELECT COUNT(ps) FROM PerspectiveScoreEntity ps WHERE ps.sessionId = :sessionId AND ps.questionIndex = :questionIndex AND ps.status IN :statuses")
    long countBySessionIdAndQuestionIndexAndStatusNotIn(@Param("sessionId") Long sessionId, @Param("questionIndex") Integer questionIndex, @Param("statuses") List<PerspectiveScoreStatus> statuses);

    /**
     * 根据会话ID和状态查找评分
     */
    List<PerspectiveScoreEntity> findBySessionIdAndStatus(Long sessionId, PerspectiveScoreStatus status);

    /**
     * 删除会话的所有评分
     */
    void deleteBySessionId(Long sessionId);
}

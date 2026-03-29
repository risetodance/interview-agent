package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewAnswerEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 面试答案Repository
 */
@Repository
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswerEntity, Long> {
    
    /**
     * 根据会话查找所有答案
     */
    List<InterviewAnswerEntity> findBySessionOrderByQuestionIndex(InterviewSessionEntity session);
    
    /**
     * 根据会话ID查找所有答案
     */
    List<InterviewAnswerEntity> findBySessionIdOrderByQuestionIndex(Long sessionId);
    
    /**
     * 根据会话 sessionId 字符串查找所有答案
     */
    List<InterviewAnswerEntity> findBySession_SessionIdOrderByQuestionIndex(String sessionId);

    /**
     * 根据会话 sessionId 和问题索引查找单条答案（用于 upsert）
     */
    Optional<InterviewAnswerEntity> findBySession_SessionIdAndQuestionIndex(String sessionId, Integer questionIndex);

    /**
     * 根据会话ID查找所有已评分的答案
     */
    List<InterviewAnswerEntity> findBySession_SessionIdAndScoreIsNotNullOrderByQuestionIndex(String sessionId);

    /**
     * 根据会话ID和视角ID查找所有答案（隐私隔离，只获取该视角的问答历史）
     */
    List<InterviewAnswerEntity> findBySessionIdAndCreatedByPerspectiveIdOrderByQuestionIndexAsc(Long sessionId, Long createdByPerspectiveId);

    /**
     * 根据会话ID和可见视角列表查找答案
     */
    @Query("SELECT a FROM InterviewAnswerEntity a WHERE a.session.sessionId = :sessionId AND a.createdByPerspectiveId = :perspectiveId ORDER BY a.questionIndex ASC")
    List<InterviewAnswerEntity> findBySessionIdForPerspective(@Param("sessionId") String sessionId, @Param("perspectiveId") Long perspectiveId);

    /**
     * 获取指定会话和视角下的最新答案（按问题索引倒序，取第一条）
     */
    @Query("SELECT a FROM InterviewAnswerEntity a WHERE a.session.id = :sessionDbId AND a.createdByPerspectiveId = :perspectiveId ORDER BY a.questionIndex DESC LIMIT 1")
    Optional<InterviewAnswerEntity> findLastAnswerBySessionAndPerspective(@Param("sessionDbId") Long sessionDbId, @Param("perspectiveId") Long perspectiveId);
}

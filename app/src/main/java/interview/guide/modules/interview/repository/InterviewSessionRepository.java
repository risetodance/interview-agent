package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 面试会话Repository
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {

    /**
     * 根据会话ID查找
     */
    Optional<InterviewSessionEntity> findBySessionId(String sessionId);

    /**
     * 根据会话ID查找（同时加载关联的简历）
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN FETCH s.resume WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionEntity> findBySessionIdWithResume(@Param("sessionId") String sessionId);
    
    /**
     * 根据简历查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeOrderByCreatedAtDesc(ResumeEntity resume);
    
    /**
     * 根据简历ID查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeIdOrderByCreatedAtDesc(Long resumeId);
    
    /**
     * 查找简历的未完成面试（CREATED或IN_PROGRESS状态）
     */
    Optional<InterviewSessionEntity> findFirstByResumeIdAndStatusInOrderByCreatedAtDesc(
        Long resumeId, 
        List<SessionStatus> statuses
    );
    
    /**
     * 根据简历ID和状态查找会话
     */
    Optional<InterviewSessionEntity> findByResumeIdAndStatusIn(
        Long resumeId,
        List<SessionStatus> statuses
    );

    /**
     * 查询用户的所有已完成面试会话（通过简历关联用户）
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN s.resume r WHERE r.userId = :userId AND s.status = :status ORDER BY s.createdAt DESC")
    List<InterviewSessionEntity> findByUserIdAndStatus(
        @Param("userId") Long userId,
        @Param("status") SessionStatus status
    );

    /**
     * 查询用户的已完成面试数量
     */
    @Query("SELECT COUNT(s) FROM InterviewSessionEntity s JOIN s.resume r WHERE r.userId = :userId AND s.status = :status")
    Long countByUserIdAndStatus(
        @Param("userId") Long userId,
        @Param("status") SessionStatus status
    );

    /**
     * 查询用户的平均评分
     */
    @Query("SELECT AVG(s.overallScore) FROM InterviewSessionEntity s JOIN s.resume r WHERE r.userId = :userId AND s.overallScore IS NOT NULL")
    Double findAverageScoreByUserId(@Param("userId") Long userId);

    /**
     * 查询用户最近的N次面试评分
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN s.resume r WHERE r.userId = :userId AND s.overallScore IS NOT NULL ORDER BY s.createdAt DESC")
    List<InterviewSessionEntity> findRecentScoresByUserId(@Param("userId") Long userId);

    /**
     * 查询即将开始的面试（用于提醒）
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN FETCH s.resume r WHERE s.status = :status AND s.scheduledTime IS NOT NULL AND s.scheduledTime <= :beforeTime AND (s.reminderSent IS NULL OR s.reminderSent = false)")
    List<InterviewSessionEntity> findByStatusAndScheduledTimeBefore(
        @Param("status") SessionStatus status,
        @Param("beforeTime") LocalDateTime beforeTime
    );

}

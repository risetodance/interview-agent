package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus;
import interview.guide.modules.interview.model.InterviewSessionEntity.WorkflowStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * 根据数据库ID查找（同时加载关联的简历，避免懒加载问题）
     */
    @Query("SELECT s FROM InterviewSessionEntity s LEFT JOIN FETCH s.resume WHERE s.id = :id")
    Optional<InterviewSessionEntity> findByIdWithResume(@Param("id") Long id);

    /**
     * 根据简历查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeOrderByCreatedAtDesc(ResumeEntity resume);
    
    /**
     * 根据简历ID查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeIdOrderByCreatedAtDesc(Long resumeId);

    /**
     * 根据简历ID查找最近的面试记录（用于历史题去重）
     */
    List<InterviewSessionEntity> findTop10ByResumeIdOrderByCreatedAtDesc(Long resumeId);
    
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
     * 查询用户的所有面试会话
     */
    @Query("SELECT s FROM InterviewSessionEntity s JOIN s.resume r WHERE r.userId = :userId ORDER BY s.createdAt DESC")
    List<InterviewSessionEntity> findAllByUserId(@Param("userId") Long userId);

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

    /**
     * 查找可恢复的会话：PROCESSING 且（无主 或 租约已过期）——多实例下存活实例持有的会话不会被他人恢复。
     */
    @Query("SELECT s.sessionId FROM InterviewSessionEntity s " +
            "WHERE s.workflowStatus = :status " +
            "AND (s.workflowOwner IS NULL OR s.workflowLeaseUntil < CURRENT_TIMESTAMP)")
    List<String> findRecoverableSessionIds(@Param("status") WorkflowStatus status);

    /**
     * 原子抢占/续租工作流执行权：仅对仍处指定状态（调用方传 PROCESSING）的会话生效，
     * 且要求 owner 为空、是自己、或租约已过期。
     * <p>状态条件堵住 release 与心跳的竞态窗口：流程已回到 AWAITING_ANSWER / DONE（租约已释放）后，
     * 看门狗本轮循环不会再把 owner 写回，避免等待答题的会话残留租约脏数据。
     * 所有调用场景（提交答案 CAS 后 / 恢复前抢占 / 心跳续租）时状态均为 PROCESSING，语义无损。
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE InterviewSessionEntity s SET s.workflowOwner = :owner, s.workflowLeaseUntil = :until " +
            "WHERE s.sessionId = :sessionId AND s.workflowStatus = :status " +
            "AND (s.workflowOwner IS NULL OR s.workflowOwner = :owner OR s.workflowLeaseUntil < CURRENT_TIMESTAMP)")
    int acquireWorkflowLease(@Param("sessionId") String sessionId,
                             @Param("owner") String owner,
                             @Param("until") LocalDateTime until,
                             @Param("status") InterviewSessionEntity.WorkflowStatus status);

    /**
     * 释放工作流执行权（仅清理自己持有的租约）。
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE InterviewSessionEntity s SET s.workflowOwner = NULL, s.workflowLeaseUntil = NULL " +
            "WHERE s.sessionId = :sessionId AND s.workflowOwner = :owner")
    int releaseWorkflowLease(@Param("sessionId") String sessionId, @Param("owner") String owner);

    /**
     * 悲观行锁查询（用于 CAS 并发控制）
     * SELECT ... FOR UPDATE，事务内锁定该行，防止并发修改
     */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InterviewSessionEntity s WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionEntity> findBySessionIdForUpdate(@Param("sessionId") String sessionId);

}

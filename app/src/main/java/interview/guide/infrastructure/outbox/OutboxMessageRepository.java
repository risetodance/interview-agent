package interview.guide.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 消息仓库。
 * <p>「锁定→投递→标记」必须在同一事务内完成（见 OutboxPublisher#dispatchPending），
 * FOR UPDATE SKIP LOCKED 保证多实例部署时同一记录只被一个 relay 处理。
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, Long> {

    /**
     * 锁定一批待投递记录（退避已到期优先按 id 顺序）。
     * <p>必须在事务内调用，锁随事务结束释放。
     */
    @Query(value = """
            SELECT * FROM outbox_message
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= now())
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessageEntity> lockPendingBatch(@Param("batchSize") int batchSize);

    /** 删除已投递超过保留期的记录（返回删除行数） */
    @Modifying
    @Query("DELETE FROM OutboxMessageEntity m WHERE m.status = 'SENT' AND m.sentAt < :expiredBefore")
    int deleteSentBefore(@Param("expiredBefore") LocalDateTime expiredBefore);
}

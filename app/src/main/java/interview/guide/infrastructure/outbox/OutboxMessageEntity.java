package interview.guide.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Outbox 消息实体：与业务数据在同一本地事务写入，由 relay 异步投递到 Redis Stream。
 * <p>职责边界：outbox 只管「可靠投递」（PENDING→SENT），任务的处理状态仍在各业务表。
 */
@Entity
@Table(name = "outbox_message")
public class OutboxMessageEntity {

    /** 投递状态 */
    public enum Status {
        /** 待投递 */
        PENDING,
        /** 已 XADD 到 Stream（outbox 职责终结） */
        SENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 消息类型（决定目标 Stream） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private OutboxMessageType messageType;

    /** 消息载荷 JSON 字符串（只放业务 ID 等轻量字段） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    /** relay 投递失败次数（指数退避依据） */
    @Column(nullable = false)
    private int retryCount = 0;

    /** 下次允许投递时间；null 表示立即可投 */
    private LocalDateTime nextRetryAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 投递成功时间 */
    private LocalDateTime sentAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OutboxMessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(OutboxMessageType messageType) {
        this.messageType = messageType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    /** 标记投递成功 */
    public void markSent(LocalDateTime now) {
        this.status = Status.SENT;
        this.sentAt = now;
    }

    /** 标记一次投递失败：计数 +1 并按指数退避约定下次投递时间（第 1 次失败等基数本身，之后 2 倍递增） */
    public void markRetryFailed(long backoffBaseMs, long maxBackoffMs, LocalDateTime now) {
        this.retryCount = this.retryCount + 1;
        int shift = Math.min(Math.max(this.retryCount - 1, 0), 20);
        long backoff = Math.min(backoffBaseMs * (1L << shift), maxBackoffMs);
        this.nextRetryAt = now.plusNanos(backoff * 1_000_000);
    }
}

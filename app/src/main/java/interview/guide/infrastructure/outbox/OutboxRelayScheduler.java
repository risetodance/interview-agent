package interview.guide.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox relay 调度器：可靠性的兜底一半。
 * <ul>
 *   <li>轮询兜底：事务提交后立即投递是低延迟主路径，本任务覆盖「提交后进程崩溃、
 *       Redis 临时不可用、立即投递异常」等漏网场景，退避到期自动补发</li>
 *   <li>按保留期清理 SENT 记录，防止表无限膨胀</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxPublisher outboxPublisher;

    /** 轮询兜底：扫描并投递退避已到期的 PENDING 记录 */
    @Scheduled(fixedDelayString = "${app.outbox.relay.poll-interval-ms:2000}")
    public void relayPendingMessages() {
        try {
            int dispatched = outboxPublisher.dispatchPending();
            if (dispatched > 0) {
                log.info("Outbox 轮询兜底投递完成: count={}", dispatched);
            }
        } catch (Exception e) {
            log.error("Outbox 轮询兜底异常: {}", e.getMessage(), e);
        }
    }

    /** 每日清理 SENT 超过保留期的记录 */
    @Scheduled(cron = "0 37 3 * * *")
    public void purgeExpiredSentMessages() {
        try {
            int purged = outboxPublisher.purgeExpiredSent();
            if (purged > 0) {
                log.info("Outbox 过期 SENT 记录清理完成: count={}", purged);
            }
        } catch (Exception e) {
            log.error("Outbox 清理异常: {}", e.getMessage(), e);
        }
    }
}

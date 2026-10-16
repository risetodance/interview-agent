package interview.guide.infrastructure.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Outbox 可靠消息投递配置（app.outbox.*）
 */
@Component
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {

    private final Relay relay = new Relay();
    private final Consumer consumer = new Consumer();

    /** SENT 记录保留天数，超期由定时任务清理 */
    private int retentionDays = 7;

    public static class Relay {
        /** 轮询兜底扫描间隔（毫秒） */
        private long pollIntervalMs = 2000;
        /** 每次扫描最多投递的 PENDING 记录数 */
        private int batchSize = 50;
        /** 投递失败退避基数（毫秒），实际等待 = 基数 * 2^retryCount */
        private long backoffBaseMs = 1000;
        /** 退避封顶（毫秒） */
        private long maxBackoffMs = 300000;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getBackoffBaseMs() {
            return backoffBaseMs;
        }

        public void setBackoffBaseMs(long backoffBaseMs) {
            this.backoffBaseMs = backoffBaseMs;
        }

        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }

        public void setMaxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
        }
    }

    public static class Consumer {
        /** PEL 滞留消息回收阈值（毫秒），超过视为消费者已死，可被 XAUTOCLAIM 认领 */
        private long claimIdleThresholdMs = 60000;

        public long getClaimIdleThresholdMs() {
            return claimIdleThresholdMs;
        }

        public void setClaimIdleThresholdMs(long claimIdleThresholdMs) {
            this.claimIdleThresholdMs = claimIdleThresholdMs;
        }
    }

    public Relay getRelay() {
        return relay;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}

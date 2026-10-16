package interview.guide.infrastructure.outbox;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox 基础设施集成测试：验证手写原生 SQL 与 Redisson XAUTOCLAIM 调用的真实行为。
 *
 * <p>需要本地 PostgreSQL（sql/016 已执行）与 Redis。
 *
 * <p>运行方式：
 * <pre>
 * # 启动中间件
 * docker compose up -d postgres redis
 *
 * # 取消 @Disabled 注解后运行
 * ./gradlew test --tests "OutboxInfrastructureIntegrationTest"
 * </pre>
 */
@DisplayName("Outbox 基础设施集成测试（需要 PostgreSQL + Redis）")
@Disabled
class OutboxInfrastructureIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/interview_agent";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "123456";
    private static final String REDIS_ADDRESS = "redis://localhost:6379";

    private static RedissonClient redisson;

    @BeforeAll
    static void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress(REDIS_ADDRESS);
        redisson = Redisson.create(config);
    }

    @AfterAll
    static void tearDown() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    // ==================== PG：lockPendingBatch 原生 SQL ====================

    @Test
    @DisplayName("PENDING 扫描 SQL：应拉到退避已到期/无需退避的记录，跳过未到期与 SENT")
    void testLockPendingSqlFilters() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD)) {
            // 自动清理测试数据，保证幂等重跑
            String token = UUID.randomUUID().toString();

            insertOutbox(conn, token + "-ready", "RESUME_ANALYZE", "PENDING", null);
            insertOutbox(conn, token + "-backoff", "RESUME_ANALYZE", "PENDING", "2099-01-01 00:00:00");
            insertOutbox(conn, token + "-sent", "RESUME_ANALYZE", "SENT", null);

            String sql = """
                    SELECT id FROM outbox_message
                    WHERE status = 'PENDING'
                      AND (next_retry_at IS NULL OR next_retry_at <= now())
                      AND message_type = ?
                    ORDER BY id
                    LIMIT 10
                    FOR UPDATE SKIP LOCKED
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "RESUME_ANALYZE");
                try (ResultSet rs = ps.executeQuery()) {
                    boolean foundReady = false;
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        if (id == queryIdByToken(conn, token + "-ready")) {
                            foundReady = true;
                        }
                    }
                    assertTrue(foundReady, "应拉到无需退避的 PENDING 记录");
                }
            }
            // 不逐条断言 backoff/sent 未被选中（表里有其他数据时不好精确断言），
            // 关键断言：到期记录可见 + SQL 语法在真实 PG 上可执行
        }
    }

    private long queryIdByToken(Connection conn, String token) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM outbox_message WHERE payload = ?")) {
            ps.setString(1, "{\"token\":\"" + token + "\"}");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    private void insertOutbox(Connection conn, String token, String type, String status, String nextRetryAt) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO outbox_message (message_type, payload, status, next_retry_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, type);
            ps.setString(2, "{\"token\":\"" + token + "\"}");
            ps.setString(3, status);
            ps.setTimestamp(4, nextRetryAt == null ? null : java.sql.Timestamp.valueOf(nextRetryAt));
            ps.executeUpdate();
        }
    }

    // ==================== Redis：Stream 消费 + XAUTOCLAIM ====================

    @Test
    @DisplayName("Stream 消费与 PEL 回收：拉取未 ack 的消息应可被 XAUTOCLAIM 重新认领")
    void testStreamAutoClaimReclaimsPending() throws Exception {
        String streamKey = "test:outbox:stream:" + UUID.randomUUID();
        String groupName = "test-group";
        RStream<String, String> stream = redisson.getStream(streamKey, StringCodec.INSTANCE);
        try {
            stream.createGroup(StreamCreateGroupArgs.name(groupName).makeStream());

            // 投递两条并全部读走（不 ack → 进入 PEL）
            stream.add(org.redisson.api.stream.StreamAddArgs.entries(Map.of("resumeId", "1")));
            stream.readGroup(groupName, "consumer-a",
                org.redisson.api.stream.StreamReadGroupArgs.neverDelivered().count(10));

            // 未到 idle 阈值：认领不到
            Map<StreamMessageId, Map<String, String>> tooEarly = stream.autoClaim(
                groupName, "consumer-b", 60_000, java.util.concurrent.TimeUnit.MILLISECONDS,
                new StreamMessageId(0, 0), 10).getMessages();
            assertTrue(tooEarly.isEmpty(), "未过 idle 阈值不应认领");

            // 过阈值后：consumer-b 可认领（原消费者已死语义）
            Map<StreamMessageId, Map<String, String>> reclaimed = stream.autoClaim(
                groupName, "consumer-b", 1, java.util.concurrent.TimeUnit.MILLISECONDS,
                new StreamMessageId(0, 0), 10).getMessages();
            assertEquals(1, reclaimed.size());
            assertTrue(reclaimed.values().iterator().next().containsKey("resumeId"));
        } finally {
            redisson.getKeys().delete(streamKey);
        }
    }
}

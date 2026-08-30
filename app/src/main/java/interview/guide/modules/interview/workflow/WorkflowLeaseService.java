package interview.guide.modules.interview.workflow;

import interview.guide.modules.interview.model.InterviewSessionEntity.WorkflowStatus;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 工作流执行权租约服务（看门狗模式，多实例安全的断点恢复）
 * <p>解决的问题：多实例部署时，重启实例的 RecoveryRunner 若扫描全部 PROCESSING 会话，
 * 会把其他存活实例正在执行的流程误当成"崩溃遗留"重复恢复（重复调 LLM、SSE 推错实例）。
 * <p><b>看门狗机制</b>（K8s lease / etcd session 同款思路）：
 * <ul>
 *   <li>持有：提交答案 CAS 抢占 PROCESSING、恢复流程启动前，原子写入 owner + 到期时间，并注册到本实例活跃集合</li>
 *   <li>心跳：后台线程每 {@link #HEARTBEAT_INTERVAL} 为本实例全部活跃会话批量续租——
 *       只要实例活着且流程在跑，租约就永不过期，长流程（慢 LLM / 重试叠加）不会被误判死亡</li>
 *   <li>释放：流程回到 AWAITING_ANSWER / DONE 时清空（仅清自己持有的）并注销心跳</li>
 *   <li>死亡判定 = 心跳缺失：实例真崩溃后租约最多 {@link #LEASE_DURATION} 内到期，恢复器即可安全接管</li>
 * </ul>
 * 租期因此可以设得很短（容忍 3 个心跳周期），接管延迟低且确定，优于"拍脑袋的静态长租期"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowLeaseService {

    /** 实例标识：pid@host + 随机后缀（同机重复启动也能区分），用于日志定位与租约归属 */
    public static final String INSTANCE_ID = ManagementFactory.getRuntimeMXBean().getName()
            + "-" + UUID.randomUUID().toString().substring(0, 8);

    /** 心跳周期 */
    private static final long HEARTBEAT_INTERVAL = 30L;

    /** 租约时长：容忍 3 个心跳周期丢失（网络抖动 / GC 停顿），实例真崩溃后 90s 内可被接管 */
    public static final Duration LEASE_DURATION = Duration.ofSeconds(90);

    private final InterviewSessionRepository sessionRepository;

    /** 本实例正在执行工作流的会话集合（心跳续租范围；流程结束/释放即移除） */
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    private ScheduledExecutorService heartbeatExecutor;

    @PostConstruct
    public void init() {
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "workflow-lease-watchdog");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::renewAllLeases,
                HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
        log.info("工作流租约看门狗已启动: instance={}, 心跳={}s, 租期={}s",
                INSTANCE_ID, HEARTBEAT_INTERVAL, LEASE_DURATION.toSeconds());
    }

    @PreDestroy
    public void shutdown() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
    }

    /**
     * 原子抢占 / 续租执行权，成功则注册心跳；抢不到说明其他存活实例正在处理该会话。
     *
     * @return true = 持有租约；false = 他人持有且未过期，应跳过
     */
    public boolean acquire(String sessionId) {
        LocalDateTime until = LocalDateTime.now().plus(LEASE_DURATION);
        int updated = sessionRepository.acquireWorkflowLease(
                sessionId, INSTANCE_ID, until, WorkflowStatus.PROCESSING);
        if (updated > 0) {
            activeSessions.add(sessionId);
            log.debug("工作流租约已持有: sessionId={}, owner={}, until={}", sessionId, INSTANCE_ID, until);
            return true;
        }
        log.info("工作流租约被其他实例持有，跳过: sessionId={}", sessionId);
        return false;
    }

    /**
     * 释放执行权并注销心跳（幂等；仅清理本实例持有的租约，不动他人）。
     */
    public void release(String sessionId) {
        activeSessions.remove(sessionId);
        sessionRepository.releaseWorkflowLease(sessionId, INSTANCE_ID);
    }

    /**
     * 注册心跳（用于不经 {@link #acquire} 而直接持租的路径——CAS 行锁内写入租约后调用）。
     */
    public void register(String sessionId) {
        activeSessions.add(sessionId);
    }

    /**
     * 心跳：为本实例全部活跃会话逐个续租（会话数为个位到十位级，逐条开销可忽略且能精确感知丢失）。
     * 续租失败（租约被过期抢占 / 会话被删）的会话从活跃集合移除——本实例已失去执行权，
     * 后续状态写回的 release 因 owner 不匹配自然 no-op，不会误伤新持有者。
     */
    private void renewAllLeases() {
        if (activeSessions.isEmpty()) {
            return;
        }
        LocalDateTime until = LocalDateTime.now().plus(LEASE_DURATION);
        for (String sessionId : activeSessions) {
            try {
                int renewed = sessionRepository.acquireWorkflowLease(
                        sessionId, INSTANCE_ID, until, WorkflowStatus.PROCESSING);
                if (renewed == 0) {
                    activeSessions.remove(sessionId);
                    log.warn("心跳续租失败，执行权已丢失（可能被过期抢占）: sessionId={}, instance={}",
                            sessionId, INSTANCE_ID);
                }
            } catch (Exception e) {
                // DB 抖动等异常不注销——租约尚有 3 个心跳周期的容忍窗口
                log.error("心跳续租异常（容忍窗口内重试）: sessionId={}, error={}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 按工作流状态流转自动处理租约：进入 PROCESSING 持有，离开（等待答题 / 结束）释放。
     * <p>由 {@code InterviewPersistenceService.updateWorkflowStatus} 统一调用，
     * 覆盖提交答案、断点中断、异常回滚、恢复降级等全部状态写回点。
     * <p>进入 PROCESSING 抢不到租约时抛异常阻断执行（状态更新随事务回滚）——
     * 租约被其他存活实例持有时本实例不应执行该流程，静默继续会造成双跑；
     * 释放路径保持静默（释放不了只留下等待过期的孤立租约，无害）。
     */
    public void onStatusChange(String sessionId, WorkflowStatus status) {
        if (status == WorkflowStatus.PROCESSING) {
            if (!acquire(sessionId)) {
                throw new interview.guide.common.exception.BusinessException(
                        interview.guide.common.exception.ErrorCode.INTERNAL_ERROR,
                        "工作流执行权被其他实例持有，放弃本次执行: sessionId=" + sessionId);
            }
        } else {
            release(sessionId);
        }
    }
}

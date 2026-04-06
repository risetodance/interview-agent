package interview.guide.modules.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试 SSE 推送服务
 * 管理 SSE 连接和事件发布
 */
@Slf4j
@Service
public class InterviewStreamService {

    // sessionId -> Sinks.Many 用于发布事件
    private final ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<String>>> sessionSinks = new ConcurrentHashMap<>();

    /**
     * 获取 SSE 流，每次都创建新的 sink
     * 使用 unicast() 单播模式，每个 sink 只能有一个订阅者
     * 每次调用都覆盖旧的 sink，确保新的连接使用新的 sink
     */
    public Flux<ServerSentEvent<String>> getStream(String sessionId) {
        Sinks.Many<ServerSentEvent<String>> newSink = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<ServerSentEvent<String>> oldSink = sessionSinks.put(sessionId, newSink);
        if (oldSink != null) {
            log.info("Replaced old SSE sink for session: {}", sessionId);
        } else {
            log.info("Created new SSE sink for session: {}", sessionId);
        }
        return newSink.asFlux();
    }

    /**
     * 移除 sink，当会话结束时调用
     */
    public void removeSink(String sessionId) {
        Sinks.Many<ServerSentEvent<String>> sink = sessionSinks.remove(sessionId);
        if (sink != null) {
            log.info("Removed SSE sink for session: {}", sessionId);
        }
    }

    /**
     * 定时心跳检测，检测 SSE 连接是否有效
     * 如果 tryEmitNext 失败，说明连接已断开，移除 sink
     * 使用 remove(key, value) CAS 操作防止误删新的连接
     */
    @Scheduled(fixedRateString = "${app.interview.sse.heartbeat-interval:30000}")
    public void heartbeatCheck() {
        log.info("Running SSE heartbeat check, active sessions: {}", sessionSinks.size());

        // 临时 map 存储需要检测的 sink 引用
        var toCheck = new HashMap<>(sessionSinks);

        // 检测并移除失效的连接
        for (Map.Entry<String, Sinks.Many<ServerSentEvent<String>>> entry : toCheck.entrySet()) {
            String sessionId = entry.getKey();
            Sinks.Many<ServerSentEvent<String>> sink = entry.getValue();

            // 发送心跳 ping 事件，检测连接是否有效
            Sinks.EmitResult result = sink.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("ping")
                    .data("{\"timestamp\": " + System.currentTimeMillis() + "}")
                    .build());

            if (result.isFailure()) {
                if (result == Sinks.EmitResult.FAIL_CANCELLED) {
                    // FAIL_CANCELLED 表示 sink 已失效（已取消/完成），不能再发送事件，直接 remove
                    log.warn("SSE heartbeat failed for session: {}, result: FAIL_CANCELLED, removing sink", sessionId);
                    sessionSinks.remove(sessionId, sink);
                } else {
                    // 其他失败情况，尝试发送 complete 事件
                    log.warn("SSE heartbeat failed for session: {}, result: {}, sending complete event", sessionId, result);
                    sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
                }
            } else {
                log.debug("SSE heartbeat success for session: {}", sessionId);
            }
        }
    }

    /**
     * 发布问题事件
     */
    public void publishQuestion(String sessionId, Map<String, Object> questionData) {
        Sinks.Many<ServerSentEvent<String>> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.emitNext(ServerSentEvent.<String>builder()
                    .event("question")
                    .data(toJson(questionData))
                    .build(), Sinks.EmitFailureHandler.FAIL_FAST);
        }
    }

    /**
     * 发布面试结束事件
     */
    public void publishComplete(String sessionId, Map<String, Object> reportData) {
        Sinks.Many<ServerSentEvent<String>> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.emitNext(ServerSentEvent.<String>builder()
                    .event("interview_complete")
                    .data(toJson(reportData))
                    .build(), Sinks.EmitFailureHandler.FAIL_FAST);
            // 面试完成时调用 emitComplete，这样订阅者会正常结束
            sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        }
    }

    /**
     * 发布错误事件
     */
    public void publishError(String sessionId, String errorMessage) {
        Sinks.Many<ServerSentEvent<String>> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.emitNext(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"message\": \"" + errorMessage + "\"}")
                    .build(), Sinks.EmitFailureHandler.FAIL_FAST);
        }
    }

    /**
     * 发送连接成功事件
     */
    public void sendConnectedEvent(String sessionId) {
        Sinks.Many<ServerSentEvent<String>> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.emitNext(ServerSentEvent.<String>builder()
                    .event("connected")
                    .data("{\"sessionId\": \"" + sessionId + "\", \"status\": \"connected\"}")
                    .build(), Sinks.EmitFailureHandler.FAIL_FAST);
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return new ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}

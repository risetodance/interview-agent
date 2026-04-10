package interview.guide.modules.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
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

    // 心跳间隔（毫秒），从配置读取，默认 30 秒
    @Value("${app.interview.sse.heartbeat-interval:30000}")
    private long heartbeatInterval;

    // ObjectMapper 复用
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取 SSE 流，每次都创建新的 sink
     * 使用 unicast() 单播模式，每个 sink 只能有一个订阅者
     * 合并业务事件流和心跳流
     */
    public Flux<ServerSentEvent<String>> getStream(String sessionId) {
        Sinks.Many<ServerSentEvent<String>> newSink = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<ServerSentEvent<String>> oldSink = sessionSinks.put(sessionId, newSink);
        if (oldSink != null) {
            log.info("Replaced old SSE sink for session: {}", sessionId);
        } else {
            log.info("Created new SSE sink for session: {}", sessionId);
        }

        // 业务事件流
        Flux<ServerSentEvent<String>> eventFlux = newSink.asFlux()
                .doFinally((signalType) -> {
                    log.info("SSE stream finally for session: {},type is {}", sessionId, signalType);
                    sessionSinks.remove(sessionId, newSink);
                });

        // 心跳流，如果发送失败说明客户端已断开，清理 sink
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofMillis(heartbeatInterval))
                .map(tick -> ServerSentEvent.<String>builder()
                        .event("ping")
                        .data("{\"timestamp\": " + System.currentTimeMillis() + "}")
                        .build())
                .takeWhile(tick -> sessionSinks.containsKey(sessionId));

        // 合并业务事件和心跳
        return Flux.merge(eventFlux, heartbeatFlux);
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
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}

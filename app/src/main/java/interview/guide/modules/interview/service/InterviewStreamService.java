package interview.guide.modules.interview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

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
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sessionSinks = new ConcurrentHashMap<>();

    /**
     * 创建或获取会话的 sink
     */
    public Sinks.Many<ServerSentEvent<String>> getOrCreateSink(String sessionId) {
        return sessionSinks.computeIfAbsent(sessionId, id -> {
            log.info("Creating new SSE sink for session: {}", sessionId);
            return Sinks.many().multicast().onBackpressureBuffer();
        });
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
     * 获取 SSE 流
     */
    public Flux<ServerSentEvent<String>> getStream(String sessionId) {
        Sinks.Many<ServerSentEvent<String>> sink = getOrCreateSink(sessionId);
        return sink.asFlux();
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
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}

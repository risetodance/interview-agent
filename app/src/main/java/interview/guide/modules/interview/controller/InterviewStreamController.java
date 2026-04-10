package interview.guide.modules.interview.controller;

import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.interview.service.InterviewStreamService;
import interview.guide.modules.interview.service.InterviewSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import reactor.core.publisher.Flux;

/**
 * 面试 SSE 推送控制器
 * 提供实时面试事件的 SSE 端点
 * 使用 InterviewStreamService 统一管理 SSE 连接
 *
 * 注意：SSE 连接使用 URL 参数传递 token，因为 EventSource 不支持自定义 header
 * 前端调用示例：/api/interview/sessions/{sessionId}/stream?token=xxx
 */
@Slf4j
@RestController
@RequestMapping("/api/interview/sessions/{sessionId}")
@RequiredArgsConstructor
public class InterviewStreamController {

    private final InterviewStreamService streamService;
    private final InterviewSessionService sessionService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * SSE 流端点
     * GET /api/interview/sessions/{sessionId}/stream
     *
     * 前端通过此接口建立 SSE 连接，接收工作流推送的事件
     * 由于 EventSource 不支持自定义 header，需要通过 URL 参数传递 token
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> stream(
            @PathVariable String sessionId,
            @RequestParam(required = false) String token) {

        // 如果没有 token，返回 401 未授权
        if (token == null || token.isBlank()) {
            log.warn("SSE stream rejected: token is required, sessionId={}", sessionId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 解析 token 获取 userId
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            // 验证会话所有权
            sessionService.validateSessionOwnership(userId, sessionId);
        } catch (Exception e) {
            log.warn("SSE stream unauthorized: sessionId={}, error={}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("SSE connection established: sessionId={}", sessionId);

        Flux<ServerSentEvent<String>> flux = streamService.getStream(sessionId)
                // 确保订阅建立后再发送 connected 事件
                .doOnSubscribe(s -> streamService.sendConnectedEvent(sessionId))
                .doOnComplete(() -> {
                    log.info("SSE connection completed: sessionId={}", sessionId);
                    // 面试完了就应该直接remove
                    streamService.removeSink(sessionId);
                });

        return ResponseEntity.ok(flux);
    }
}

package interview.guide.modules.interview.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.modules.interview.service.InterviewStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 面试 SSE 推送控制器
 * 提供实时面试事件的 SSE 端点
 */
@Slf4j
@RestController
@RequestMapping("/api/interview/sessions/{sessionId}")
@RequiredArgsConstructor
public class InterviewStreamController {

    private final InterviewStreamService streamService;

    /**
     * SSE 流端点
     * GET /api/interview/sessions/{sessionId}/stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @CurrentUser Long userId,
            @PathVariable String sessionId) {

        log.info("SSE connection established: userId={}, sessionId={}", userId, sessionId);

        return streamService.getStream(sessionId)
                .doOnCancel(() -> {
                    log.info("SSE connection cancelled: sessionId={}", sessionId);
                })
                .doOnError(e -> {
                    log.error("SSE error: sessionId={}", sessionId, e);
                });
    }
}

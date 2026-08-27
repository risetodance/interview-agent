package interview.guide.modules.knowledgebase;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.modules.knowledgebase.model.RagChatDTO.*;
import interview.guide.modules.knowledgebase.service.RagChatSessionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * RAG 聊天控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RagChatController {

    private final RagChatSessionService sessionService;

    /**
     * 创建新会话
     */
    @PostMapping("/api/rag-chat/sessions")
    public Result<SessionDTO> createSession(
            @CurrentUser Long userId,
            @Valid @RequestBody CreateSessionRequest request) {
        return Result.success(sessionService.createSession(request, userId));
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/api/rag-chat/sessions")
    public Result<List<SessionListItemDTO>> listSessions(@CurrentUser Long userId) {
        return Result.success(sessionService.listSessions(userId));
    }

    /**
     * 获取会话详情（包含消息历史）
     * GET /api/rag-chat/sessions/{sessionId}
     */
    @GetMapping("/api/rag-chat/sessions/{sessionId}")
    public Result<SessionDetailDTO> getSessionDetail(
            @CurrentUser Long userId,
            @PathVariable Long sessionId) {
        return Result.success(sessionService.getSessionDetail(sessionId, userId));
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/api/rag-chat/sessions/{sessionId}/title")
    public Result<Void> updateSessionTitle(
            @CurrentUser Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateTitleRequest request) {
        sessionService.updateSessionTitle(sessionId, request.title(), userId);
        return Result.success(null);
    }

    /**
     * 切换会话置顶状态
     * PUT /api/rag-chat/sessions/{sessionId}/pin
     */
    @PutMapping("/api/rag-chat/sessions/{sessionId}/pin")
    public Result<Void> togglePin(
            @CurrentUser Long userId,
            @PathVariable Long sessionId) {
        sessionService.togglePin(sessionId, userId);
        return Result.success(null);
    }

    /**
     * 更新会话知识库
     */
    @PutMapping("/api/rag-chat/sessions/{sessionId}/knowledge-bases")
    public Result<Void> updateSessionKnowledgeBases(
            @CurrentUser Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateKnowledgeBasesRequest request) {
        sessionService.updateSessionKnowledgeBases(sessionId, request.knowledgeBaseIds(), userId);
        return Result.success(null);
    }

    /**
     * 删除会话
     * DELETE /api/rag-chat/sessions/{sessionId}
     */
    @DeleteMapping("/api/rag-chat/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @CurrentUser Long userId,
            @PathVariable Long sessionId) {
        sessionService.deleteSession(sessionId, userId);
        return Result.success(null);
    }

    /**
     * 发送消息（流式SSE）
     * 流式响应设计：
     * 1. 先同步保存用户消息和创建 AI 消息占位
     * 2. 返回流式响应
     * 3. 流式完成后通过回调更新消息
     */
    @PostMapping(value = "/api/rag-chat/sessions/{sessionId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessageStream(
            @CurrentUser Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request,
            HttpServletResponse response) {

        // 禁用 nginx 对本响应的缓冲（proxy_buffering），SSE 事件须即时透传，
        // 否则会被反向代理攒住导致客户端长时间收不到分块（H5 流式关键）
        response.setHeader("X-Accel-Buffering", "no");

        log.info("收到 RAG 聊天流式请求: sessionId={}, question={}, 线程: {} (虚拟线程: {}, userId={})",
                sessionId, request.question(), Thread.currentThread(), Thread.currentThread().isVirtual(), userId);

        // 1. 准备消息（保存用户消息，创建 AI 消息占位）
        Long messageId = sessionService.prepareStreamMessage(sessionId, request.question(), userId);

        // 2. 获取流式响应
        StringBuilder fullContent = new StringBuilder();

        return sessionService.getStreamAnswer(sessionId, request.question())
                .doOnNext(fullContent::append)
                // 使用 ServerSentEvent 包装，转义换行符避免破坏 SSE 格式
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk.replace("\n", "\\n").replace("\r", "\\r"))
                        .build())
                .doOnComplete(() -> {
                    // 3. 流式完成后更新消息内容
                    sessionService.completeStreamMessage(messageId, fullContent.toString());
                    log.info("RAG 聊天流式完成: sessionId={}, messageId={}", sessionId, messageId);
                })
                .doOnError(e -> {
                    // 错误时也保存已接收的内容
                    String content = !fullContent.isEmpty()
                            ? fullContent.toString()
                            : "【错误】回答生成失败：" + e.getMessage();
                    sessionService.completeStreamMessage(messageId, content);
                    log.error("RAG 聊天流式错误: sessionId={}", sessionId, e);
                });
    }

    /**
     * 发送消息（同步版，非流式）
     * 一次性返回完整回答。小程序端 enableChunked 的 SSE 兼容性差
     * （面试模块已因此改轮询），为知识库问答提供不依赖分块传输的通道
     */
    @PostMapping("/api/rag-chat/sessions/{sessionId}/messages")
    public Result<Map<String, Object>> sendMessage(
            @CurrentUser Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {

        log.info("收到 RAG 聊天同步请求: sessionId={}, userId={}", sessionId, userId);

        // 1. 准备消息（保存用户消息，创建 AI 消息占位）
        Long messageId = sessionService.prepareStreamMessage(sessionId, request.question(), userId);

        // 2. 阻塞聚合流式结果（复用同一生成逻辑；MVC 虚拟线程环境下可安全阻塞）
        StringBuilder fullContent = new StringBuilder();
        try {
            sessionService.getStreamAnswer(sessionId, request.question())
                    .doOnNext(fullContent::append)
                    .blockLast();
            sessionService.completeStreamMessage(messageId, fullContent.toString());
            log.info("RAG 聊天同步完成: sessionId={}, messageId={}", sessionId, messageId);
        } catch (Exception e) {
            // 错误时保存已生成的部分内容，与流式版行为一致
            String content = !fullContent.isEmpty()
                    ? fullContent.toString()
                    : "【错误】回答生成失败：" + e.getMessage();
            sessionService.completeStreamMessage(messageId, content);
            log.error("RAG 聊天同步请求错误: sessionId={}", sessionId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "回答生成失败，请重试");
        }

        return Result.success(Map.of(
                "messageId", messageId,
                "content", fullContent.toString()
        ));
    }
}

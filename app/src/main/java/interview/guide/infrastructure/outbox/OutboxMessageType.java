package interview.guide.infrastructure.outbox;

import interview.guide.common.constant.AsyncTaskStreamConstants;

/**
 * Outbox 消息类型：决定记录投递到哪条 Redis Stream。
 * <p>outbox 侧统一维护「类型 → Stream」映射，业务方在事务内只声明类型与载荷。
 */
public enum OutboxMessageType {

    /** 简历分析（载荷：resumeId） */
    RESUME_ANALYZE(AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY),

    /** 知识库向量化（载荷：kbId） */
    KB_VECTORIZE(AsyncTaskStreamConstants.KB_VECTORIZE_STREAM_KEY),

    /** 面试评估（载荷：sessionId） */
    INTERVIEW_EVALUATE(AsyncTaskStreamConstants.INTERVIEW_EVALUATE_STREAM_KEY),
    ;

    private final String streamKey;

    OutboxMessageType(String streamKey) {
        this.streamKey = streamKey;
    }

    public String streamKey() {
        return streamKey;
    }
}

package interview.guide.modules.interview.model;

import java.time.LocalDateTime;

/**
 * 面试会话列表项DTO
 */
public record InterviewSessionListItemDTO(
    Long id,
    String sessionId,
    String title,
    String type,
    String position,
    String company,
    String status,
    Integer score,
    Integer duration,
    Integer questionCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

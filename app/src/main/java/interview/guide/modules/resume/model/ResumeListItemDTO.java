package interview.guide.modules.resume.model;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import java.time.LocalDateTime;

/**
 * 简历列表项DTO
 */
public record ResumeListItemDTO(
    Long id,
    String filename,
    Long fileSize,
    LocalDateTime uploadedAt,
    Integer accessCount,
    Integer latestScore,
    LocalDateTime lastAnalyzedAt,
    Integer interviewCount,
    AsyncTaskStatus analyzeStatus,
    InterviewSessionEntity.SessionStatus interviewStatus
) {}


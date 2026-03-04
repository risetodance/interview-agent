package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.ScoreTrendDTO;
import interview.guide.modules.interview.model.ScoreTrendDTO.DailyScore;
import interview.guide.modules.interview.model.ScoreTrendDTO.ScoreStatistics;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评分趋势服务
 * 计算用户的面试评分趋势和统计信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreTrendService {

    private final ResumeRepository resumeRepository;
    private final InterviewPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    /**
     * 获取用户的评分趋势
     * 按日期聚合，返回每日平均分和面试次数
     */
    public ScoreTrendDTO getScoreTrend(Long userId) {
        // 1. 获取用户的所有简历
        List<ResumeEntity> resumes = resumeRepository.findByUserId(userId);

        if (resumes.isEmpty()) {
            return new ScoreTrendDTO(
                Collections.emptyList(),
                new ScoreStatistics(0.0, 0, 0, 0)
            );
        }

        // 2. 获取所有简历的已完成面试会话
        List<InterviewSessionEntity> allSessions = new ArrayList<>();
        for (ResumeEntity resume : resumes) {
            List<InterviewSessionEntity> sessions = persistenceService.findByResumeId(resume.getId());
            // 只保留已完成且有评分的会话
            sessions.stream()
                .filter(s -> s.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED
                          && s.getOverallScore() != null)
                .forEach(allSessions::add);
        }

        if (allSessions.isEmpty()) {
            return new ScoreTrendDTO(
                Collections.emptyList(),
                new ScoreStatistics(0.0, 0, 0, 0)
            );
        }

        // 3. 按日期聚合，计算每日平均分
        Map<LocalDate, List<InterviewSessionEntity>> sessionsByDate = allSessions.stream()
            .collect(Collectors.groupingBy(session -> {
                LocalDateTime completedAt = session.getCompletedAt();
                return completedAt != null ? completedAt.toLocalDate() : session.getCreatedAt().toLocalDate();
            }));

        List<DailyScore> dailyScores = sessionsByDate.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                LocalDate date = entry.getKey();
                List<InterviewSessionEntity> sessions = entry.getValue();
                double avgScore = sessions.stream()
                    .mapToInt(InterviewSessionEntity::getOverallScore)
                    .average()
                    .orElse(0.0);
                return new DailyScore(date, avgScore, sessions.size());
            })
            .toList();

        // 4. 计算统计信息
        IntSummaryStatistics stats = allSessions.stream()
            .mapToInt(InterviewSessionEntity::getOverallScore)
            .summaryStatistics();

        ScoreStatistics statistics = new ScoreStatistics(
            stats.getAverage(),
            stats.getMax(),
            stats.getMin(),
            allSessions.size()
        );

        return new ScoreTrendDTO(dailyScores, statistics);
    }

    /**
     * 解析评分历史JSON
     * 格式: [{"score": 85, "createdAt": "2024-01-01T10:00:00"}]
     */
    private List<Map<String, Object>> parseScoreHistory(String scoreHistoryJson) {
        if (scoreHistoryJson == null || scoreHistoryJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(scoreHistoryJson, new TypeReference<>() {});
        } catch (JacksonException e) {
            log.error("解析评分历史JSON失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}

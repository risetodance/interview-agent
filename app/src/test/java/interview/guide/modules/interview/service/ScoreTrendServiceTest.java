package interview.guide.modules.interview.service;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.ScoreTrendDTO;
import interview.guide.modules.interview.model.ScoreTrendDTO.DailyScore;
import interview.guide.modules.interview.model.ScoreTrendDTO.ScoreStatistics;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * ScoreTrendService 单元测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>用户无简历时返回空数据</li>
 *   <li>用户有简历但无面试记录时返回空数据</li>
 *   <li>正常获取评分趋势</li>
 *   <li>评分统计信息计算</li>
 *   <li>按日期聚合计算</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("评分趋势服务测试")
class ScoreTrendServiceTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private InterviewPersistenceService persistenceService;

    private ObjectMapper objectMapper;
    private ScoreTrendService scoreTrendService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        scoreTrendService = new ScoreTrendService(resumeRepository, persistenceService, objectMapper);
    }

    @Nested
    @DisplayName("空数据场景测试")
    class EmptyDataTest {

        @Test
        @DisplayName("用户无简历时返回空数据")
        void testNoResumes() {
            when(resumeRepository.findByUserId(anyLong())).thenReturn(Collections.emptyList());

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertTrue(result.dailyScores().isEmpty());
            assertNotNull(result.statistics());
            assertEquals(0.0, result.statistics().averageScore());
            assertEquals(0, result.statistics().totalInterviews());
        }

        @Test
        @DisplayName("用户有简历但无面试记录时返回空数据")
        void testNoInterviews() {
            ResumeEntity resume = new ResumeEntity();
            resume.setId(1L);
            resume.setUserId(1L);

            when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume));
            when(persistenceService.findByResumeId(1L)).thenReturn(Collections.emptyList());

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertTrue(result.dailyScores().isEmpty());
            assertNotNull(result.statistics());
            assertEquals(0.0, result.statistics().averageScore());
            assertEquals(0, result.statistics().totalInterviews());
        }

        @Test
        @DisplayName("有面试但未完成评估时不计入统计")
        void testInterviewNotEvaluated() {
            ResumeEntity resume = new ResumeEntity();
            resume.setId(1L);
            resume.setUserId(1L);

            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setId(1L);
            session.setResume(resume);
            session.setStatus(InterviewSessionEntity.SessionStatus.COMPLETED);
            session.setOverallScore(85);
            session.setCreatedAt(LocalDateTime.now());

            when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume));
            when(persistenceService.findByResumeId(1L)).thenReturn(List.of(session));

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertTrue(result.dailyScores().isEmpty());
            assertEquals(0, result.statistics().totalInterviews());
        }
    }

    @Nested
    @DisplayName("正常评分趋势测试")
    class NormalTrendTest {

        @Test
        @DisplayName("单次面试评分趋势")
        void testSingleInterviewTrend() {
            ResumeEntity resume = new ResumeEntity();
            resume.setId(1L);
            resume.setUserId(1L);

            InterviewSessionEntity session = createSession(resume, 85, LocalDateTime.of(2024, 1, 15, 10, 0));

            when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume));
            when(persistenceService.findByResumeId(1L)).thenReturn(List.of(session));

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertEquals(1, result.dailyScores().size());

            DailyScore dailyScore = result.dailyScores().get(0);
            assertEquals(LocalDate.of(2024, 1, 15), dailyScore.date());
            assertEquals(85.0, dailyScore.averageScore());
            assertEquals(1, dailyScore.interviewCount());

            ScoreStatistics stats = result.statistics();
            assertEquals(85.0, stats.averageScore());
            assertEquals(85, stats.highestScore());
            assertEquals(85, stats.lowestScore());
            assertEquals(1, stats.totalInterviews());
        }

        @Test
        @DisplayName("多次面试评分趋势")
        void testMultipleInterviewTrend() {
            ResumeEntity resume = new ResumeEntity();
            resume.setId(1L);
            resume.setUserId(1L);

            InterviewSessionEntity session1 = createSession(resume, 80, LocalDateTime.of(2024, 1, 15, 10, 0));
            InterviewSessionEntity session2 = createSession(resume, 90, LocalDateTime.of(2024, 1, 16, 14, 0));
            InterviewSessionEntity session3 = createSession(resume, 85, LocalDateTime.of(2024, 1, 17, 9, 0));

            when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume));
            when(persistenceService.findByResumeId(1L)).thenReturn(List.of(session1, session2, session3));

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertEquals(3, result.dailyScores().size());

            // 验证日期排序
            assertEquals(LocalDate.of(2024, 1, 15), result.dailyScores().get(0).date());
            assertEquals(LocalDate.of(2024, 1, 16), result.dailyScores().get(1).date());
            assertEquals(LocalDate.of(2024, 1, 17), result.dailyScores().get(2).date());

            // 验证统计信息
            ScoreStatistics stats = result.statistics();
            assertEquals(85.0, stats.averageScore());
            assertEquals(90, stats.highestScore());
            assertEquals(80, stats.lowestScore());
            assertEquals(3, stats.totalInterviews());
        }

        @Test
        @DisplayName("同一天多次面试取平均值")
        void testSameDayMultipleInterviews() {
            ResumeEntity resume = new ResumeEntity();
            resume.setId(1L);
            resume.setUserId(1L);

            InterviewSessionEntity session1 = createSession(resume, 80, LocalDateTime.of(2024, 1, 15, 10, 0));
            InterviewSessionEntity session2 = createSession(resume, 90, LocalDateTime.of(2024, 1, 15, 14, 0));
            InterviewSessionEntity session3 = createSession(resume, 100, LocalDateTime.of(2024, 1, 15, 18, 0));

            when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume));
            when(persistenceService.findByResumeId(1L)).thenReturn(List.of(session1, session2, session3));

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertEquals(1, result.dailyScores().size());

            DailyScore dailyScore = result.dailyScores().get(0);
            assertEquals(90.0, dailyScore.averageScore()); // (80+90+100)/3 = 90
            assertEquals(3, dailyScore.interviewCount());

            ScoreStatistics stats = result.statistics();
            assertEquals(90.0, stats.averageScore());
            assertEquals(100, stats.highestScore());
            assertEquals(80, stats.lowestScore());
            assertEquals(3, stats.totalInterviews());
        }

        @Test
        @DisplayName("多简历面试评分趋势")
        void testMultipleResumesTrend() {
            ResumeEntity resume1 = new ResumeEntity();
            resume1.setId(1L);
            resume1.setUserId(1L);

            ResumeEntity resume2 = new ResumeEntity();
            resume2.setId(2L);
            resume2.setUserId(1L);

            InterviewSessionEntity session1 = createSession(resume1, 75, LocalDateTime.of(2024, 1, 15, 10, 0));
            InterviewSessionEntity session2 = createSession(resume2, 95, LocalDateTime.of(2024, 1, 16, 10, 0));

            when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume1, resume2));
            when(persistenceService.findByResumeId(1L)).thenReturn(List.of(session1));
            when(persistenceService.findByResumeId(2L)).thenReturn(List.of(session2));

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertEquals(2, result.dailyScores().size());

            ScoreStatistics stats = result.statistics();
            assertEquals(85.0, stats.averageScore()); // (75+95)/2
            assertEquals(95, stats.highestScore());
            assertEquals(75, stats.lowestScore());
            assertEquals(2, stats.totalInterviews());
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("已完成但无评分不计入统计")
        void testEvaluatedWithoutScore() {
            ResumeEntity resume = new ResumeEntity();
            resume.setId(1L);
            resume.setUserId(1L);

            InterviewSessionEntity session = createSession(resume, null, LocalDateTime.of(2024, 1, 15, 10, 0));
            session.setStatus(InterviewSessionEntity.SessionStatus.EVALUATED);
            // 不设置 overallScore

            when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume));
            when(persistenceService.findByResumeId(1L)).thenReturn(List.of(session));

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertTrue(result.dailyScores().isEmpty());
            assertEquals(0, result.statistics().totalInterviews());
        }

        @Test
        @DisplayName("使用创建时间作为完成时间（当完成时间为空时）")
        void testUseCreatedAtWhenCompletedAtNull() {
            ResumeEntity resume = new ResumeEntity();
            resume.setId(1L);
            resume.setUserId(1L);

            InterviewSessionEntity session = new InterviewSessionEntity();
            session.setId(1L);
            session.setResume(resume);
            session.setStatus(InterviewSessionEntity.SessionStatus.EVALUATED);
            session.setOverallScore(85);
            session.setCreatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));
            // completedAt 为 null

            when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume));
            when(persistenceService.findByResumeId(1L)).thenReturn(List.of(session));

            ScoreTrendDTO result = scoreTrendService.getScoreTrend(1L);

            assertNotNull(result);
            assertEquals(1, result.dailyScores().size());
            assertEquals(LocalDate.of(2024, 1, 15), result.dailyScores().get(0).date());
        }
    }

    /**
     * 创建测试用的面试会话实体
     */
    private InterviewSessionEntity createSession(ResumeEntity resume, Integer score, LocalDateTime completedAt) {
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setId(1L);
        session.setResume(resume);
        session.setStatus(InterviewSessionEntity.SessionStatus.EVALUATED);
        session.setOverallScore(score);
        session.setCreatedAt(completedAt.minusHours(1));
        session.setCompletedAt(completedAt);
        return session;
    }
}

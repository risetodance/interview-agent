package interview.guide.modules.interview.model;

import interview.guide.modules.resume.model.ResumeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InterviewSessionEntity 单元测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>knowledgeBaseIds 字段的存取</li>
 *   <li>scoreHistory 字段的存取</li>
 *   <li>JSON 格式数据解析</li>
 *   <li>会话状态流转</li>
 * </ul>
 */
@DisplayName("面试会话实体测试")
class InterviewSessionEntityTest {

    private InterviewSessionEntity session;
    private ResumeEntity resume;

    @BeforeEach
    void setUp() {
        session = new InterviewSessionEntity();
        session.setSessionId(UUID.randomUUID().toString());

        resume = new ResumeEntity();
        resume.setId(1L);
        resume.setUserId(100L);
        resume.setFileHash("abc123");
        resume.setOriginalFilename("resume.pdf");
        resume.setUploadedAt(LocalDateTime.now());

        session.setResume(resume);
        session.setStatus(InterviewSessionEntity.SessionStatus.CREATED);
    }

    @Nested
    @DisplayName("知识库关联字段测试")
    class KnowledgeBaseIdsTest {

        @Test
        @DisplayName("设置知识库ID列表 - 单一知识库")
        void testSetSingleKnowledgeBaseId() {
            String knowledgeBaseIds = "[1]";
            session.setKnowledgeBaseIds(knowledgeBaseIds);

            assertEquals(knowledgeBaseIds, session.getKnowledgeBaseIds());
        }

        @Test
        @DisplayName("设置知识库ID列表 - 多个知识库")
        void testSetMultipleKnowledgeBaseIds() {
            String knowledgeBaseIds = "[1, 2, 3]";
            session.setKnowledgeBaseIds(knowledgeBaseIds);

            assertEquals(knowledgeBaseIds, session.getKnowledgeBaseIds());
        }

        @Test
        @DisplayName("知识库ID列表可以为空")
        void testEmptyKnowledgeBaseIds() {
            session.setKnowledgeBaseIds(null);
            assertNull(session.getKnowledgeBaseIds());

            session.setKnowledgeBaseIds("[]");
            assertEquals("[]", session.getKnowledgeBaseIds());
        }

        @Test
        @DisplayName("知识库ID列表支持空值")
        void testNullKnowledgeBaseIds() {
            session.setKnowledgeBaseIds(null);
            assertNull(session.getKnowledgeBaseIds());
        }
    }

    @Nested
    @DisplayName("评分历史字段测试")
    class ScoreHistoryTest {

        @Test
        @DisplayName("设置评分历史 - 单条记录")
        void testSetSingleScoreHistory() {
            String scoreHistory = "[{\"score\": 85, \"createdAt\": \"2024-01-01T10:00:00\"}]";
            session.setScoreHistory(scoreHistory);

            assertEquals(scoreHistory, session.getScoreHistory());
        }

        @Test
        @DisplayName("设置评分历史 - 多条记录")
        void testSetMultipleScoreHistory() {
            String scoreHistory = "["
                    + "{\"score\": 75, \"createdAt\": \"2024-01-01T10:00:00\"},"
                    + "{\"score\": 82, \"createdAt\": \"2024-01-02T14:30:00\"},"
                    + "{\"score\": 90, \"createdAt\": \"2024-01-03T09:15:00\"}"
                    + "]";
            session.setScoreHistory(scoreHistory);

            assertEquals(scoreHistory, session.getScoreHistory());
        }

        @Test
        @DisplayName("评分历史可以为空")
        void testEmptyScoreHistory() {
            session.setScoreHistory(null);
            assertNull(session.getScoreHistory());

            session.setScoreHistory("[]");
            assertEquals("[]", session.getScoreHistory());
        }

        @Test
        @DisplayName("评分历史支持空值")
        void testNullScoreHistory() {
            session.setScoreHistory(null);
            assertNull(session.getScoreHistory());
        }
    }

    @Nested
    @DisplayName("会话状态流转测试")
    class SessionStatusTest {

        @Test
        @DisplayName("默认状态为 CREATED")
        void testDefaultStatus() {
            InterviewSessionEntity newSession = new InterviewSessionEntity();
            assertEquals(InterviewSessionEntity.SessionStatus.CREATED, newSession.getStatus());
        }

        @Test
        @DisplayName("状态流转 - CREATED 到 IN_PROGRESS")
        void testStatusToInProgress() {
            session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            assertEquals(InterviewSessionEntity.SessionStatus.IN_PROGRESS, session.getStatus());
        }

        @Test
        @DisplayName("状态流转 - IN_PROGRESS 到 COMPLETED")
        void testStatusToCompleted() {
            session.setStatus(InterviewSessionEntity.SessionStatus.COMPLETED);
            assertEquals(InterviewSessionEntity.SessionStatus.COMPLETED, session.getStatus());
        }

        @Test
        @DisplayName("状态流转 - COMPLETED 到 EVALUATED")
        void testStatusToEvaluated() {
            session.setStatus(InterviewSessionEntity.SessionStatus.EVALUATED);
            assertEquals(InterviewSessionEntity.SessionStatus.EVALUATED, session.getStatus());
        }
    }

    @Nested
    @DisplayName("综合场景测试")
    class IntegrationTest {

        @Test
        @DisplayName("完整面试会话场景")
        void testCompleteInterviewSession() {
            // 1. 创建会话时设置知识库
            session.setKnowledgeBaseIds("[1, 2, 3]");
            assertEquals("[1, 2, 3]", session.getKnowledgeBaseIds());

            // 2. 更新状态为进行中
            session.setStatus(InterviewSessionEntity.SessionStatus.IN_PROGRESS);
            assertEquals(InterviewSessionEntity.SessionStatus.IN_PROGRESS, session.getStatus());

            // 3. 设置总分
            session.setOverallScore(85);
            assertEquals(85, session.getOverallScore());

            // 4. 添加评分历史
            String scoreHistory = "[{\"score\": 85, \"createdAt\": \"2024-01-01T10:00:00\"}]";
            session.setScoreHistory(scoreHistory);
            assertEquals(scoreHistory, session.getScoreHistory());

            // 5. 更新状态为已完成
            session.setStatus(InterviewSessionEntity.SessionStatus.COMPLETED);
            assertEquals(InterviewSessionEntity.SessionStatus.COMPLETED, session.getStatus());

            // 6. 验证与简历的关联
            assertNotNull(session.getResume());
            assertEquals(1L, session.getResume().getId());
            assertEquals(100L, session.getResume().getUserId());
        }

        @Test
        @DisplayName("评分历史与当前评分同步")
        void testScoreHistoryWithCurrentScore() {
            // 第一次面试
            session.setOverallScore(75);
            session.setScoreHistory("[{\"score\": 75, \"createdAt\": \"2024-01-01T10:00:00\"}]");

            // 第二次面试 - 更新当前评分并保留历史
            session.setOverallScore(85);
            session.setScoreHistory("["
                    + "{\"score\": 75, \"createdAt\": \"2024-01-01T10:00:00\"},"
                    + "{\"score\": 85, \"createdAt\": \"2024-01-02T14:30:00\"}"
                    + "]");

            assertEquals(85, session.getOverallScore());
            assertTrue(session.getScoreHistory().contains("75"));
            assertTrue(session.getScoreHistory().contains("85"));
        }

        @Test
        @DisplayName("空知识库和空评分历史")
        void testEmptyKnowledgeBaseAndScoreHistory() {
            session.setKnowledgeBaseIds("[]");
            session.setScoreHistory("[]");
            session.setOverallScore(null);

            assertEquals("[]", session.getKnowledgeBaseIds());
            assertEquals("[]", session.getScoreHistory());
            assertNull(session.getOverallScore());
        }
    }
}

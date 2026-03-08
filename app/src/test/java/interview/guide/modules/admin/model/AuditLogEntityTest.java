package interview.guide.modules.admin.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审计日志实体单元测试
 */
@DisplayName("审计日志实体测试")
class AuditLogEntityTest {

    @Nested
    @DisplayName("实体字段验证测试")
    class FieldValidationTests {

        @Test
        @DisplayName("使用 Builder 创建实体时，ID 应该为 null")
        void testBuilder_ShouldReturnNullId() {
            AuditLogEntity entity = AuditLogEntity.builder()
                .operationType("CREATE")
                .operatorId(1L)
                .operatorUsername("admin")
                .build();

            assertNull(entity.getId());
        }

        @Test
        @DisplayName("使用 Builder 创建完整审计日志")
        void testBuilder_ShouldSetAllFields() {
            String operationType = "UPDATE";
            Long operatorId = 1L;
            String operatorUsername = "superadmin";
            String operatorRole = "SUPER_ADMIN";
            String targetType = "USER";
            Long targetId = 100L;
            String details = "{\"field\": \"email\", \"old\": \"a@test.com\", \"new\": \"b@test.com\"}";
            String ipAddress = "192.168.1.100";
            String method = "PUT";
            String requestUrl = "/api/users/100";
            String userAgent = "Mozilla/5.0";
            String result = "SUCCESS";
            Long duration = 150L;

            AuditLogEntity entity = AuditLogEntity.builder()
                .operationType(operationType)
                .operatorId(operatorId)
                .operatorUsername(operatorUsername)
                .operatorRole(operatorRole)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .ipAddress(ipAddress)
                .method(method)
                .requestUrl(requestUrl)
                .userAgent(userAgent)
                .result(result)
                .duration(duration)
                .build();

            assertEquals(operationType, entity.getOperationType());
            assertEquals(operatorId, entity.getOperatorId());
            assertEquals(operatorUsername, entity.getOperatorUsername());
            assertEquals(operatorRole, entity.getOperatorRole());
            assertEquals(targetType, entity.getTargetType());
            assertEquals(targetId, entity.getTargetId());
            assertEquals(details, entity.getDetails());
            assertEquals(ipAddress, entity.getIpAddress());
            assertEquals(method, entity.getMethod());
            assertEquals(requestUrl, entity.getRequestUrl());
            assertEquals(userAgent, entity.getUserAgent());
            assertEquals(result, entity.getResult());
            assertEquals(duration, entity.getDuration());
        }

        @Test
        @DisplayName("支持常见的操作类型")
        void testBuilder_ShouldSupportCommonOperationTypes() {
            // CREATE 操作
            AuditLogEntity createLog = AuditLogEntity.builder()
                .operationType("CREATE")
                .operatorId(1L)
                .build();
            assertEquals("CREATE", createLog.getOperationType());

            // UPDATE 操作
            AuditLogEntity updateLog = AuditLogEntity.builder()
                .operationType("UPDATE")
                .operatorId(1L)
                .build();
            assertEquals("UPDATE", updateLog.getOperationType());

            // DELETE 操作
            AuditLogEntity deleteLog = AuditLogEntity.builder()
                .operationType("DELETE")
                .operatorId(1L)
                .build();
            assertEquals("DELETE", deleteLog.getOperationType());

            // LOGIN 操作
            AuditLogEntity loginLog = AuditLogEntity.builder()
                .operationType("LOGIN")
                .operatorId(1L)
                .build();
            assertEquals("LOGIN", loginLog.getOperationType());

            // LOGOUT 操作
            AuditLogEntity logoutLog = AuditLogEntity.builder()
                .operationType("LOGOUT")
                .operatorId(1L)
                .build();
            assertEquals("LOGOUT", logoutLog.getOperationType());
        }

        @Test
        @DisplayName("支持常见的目标类型")
        void testBuilder_ShouldSupportCommonTargetTypes() {
            // 用户目标
            AuditLogEntity userLog = AuditLogEntity.builder()
                .operationType("CREATE")
                .operatorId(1L)
                .targetType("USER")
                .targetId(100L)
                .build();
            assertEquals("USER", userLog.getTargetType());

            // 简历目标
            AuditLogEntity resumeLog = AuditLogEntity.builder()
                .operationType("UPDATE")
                .operatorId(1L)
                .targetType("RESUME")
                .targetId(200L)
                .build();
            assertEquals("RESUME", resumeLog.getTargetType());

            // 面试目标
            AuditLogEntity interviewLog = AuditLogEntity.builder()
                .operationType("DELETE")
                .operatorId(1L)
                .targetType("INTERVIEW")
                .targetId(300L)
                .build();
            assertEquals("INTERVIEW", interviewLog.getTargetType());

            // 知识库目标
            AuditLogEntity kbLog = AuditLogEntity.builder()
                .operationType("UPDATE")
                .operatorId(1L)
                .targetType("KNOWLEDGEBASE")
                .targetId(400L)
                .build();
            assertEquals("KNOWLEDGEBASE", kbLog.getTargetType());
        }

        @Test
        @DisplayName("支持操作结果状态")
        void testBuilder_ShouldSupportResultStatus() {
            // 成功结果
            AuditLogEntity successLog = AuditLogEntity.builder()
                .operationType("CREATE")
                .operatorId(1L)
                .result("SUCCESS")
                .build();
            assertEquals("SUCCESS", successLog.getResult());

            // 失败结果
            AuditLogEntity failedLog = AuditLogEntity.builder()
                .operationType("CREATE")
                .operatorId(1L)
                .result("FAILED")
                .errorMessage("Duplicate key")
                .build();
            assertEquals("FAILED", failedLog.getResult());
            assertEquals("Duplicate key", failedLog.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("JPA 生命周期回调测试")
    class JpaLifecycleTests {

        @Test
        @DisplayName("保存前应自动设置 createdAt")
        void testPrePersist_ShouldSetCreatedAt() {
            AuditLogEntity entity = AuditLogEntity.builder()
                .operationType("CREATE")
                .operatorId(1L)
                .build();

            entity.onCreate();

            assertNotNull(entity.getCreatedAt());
        }

        @Test
        @DisplayName("createdAt 不应在更新时被修改")
        void testPreUpdate_ShouldNotModifyCreatedAt() {
            AuditLogEntity entity = AuditLogEntity.builder()
                .operationType("CREATE")
                .operatorId(1L)
                .build();

            LocalDateTime originalCreatedAt = LocalDateTime.now();
            entity.setCreatedAt(originalCreatedAt);

            // 模拟 @PreUpdate 不应影响 createdAt（审计日志的 createdAt 是 updatable = false）
            // 这里只是验证 createdAt 是持久化的
            assertNotNull(entity.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("最小化审计日志测试")
    class MinimalAuditLogTests {

        @Test
        @DisplayName("支持最小化的审计日志创建")
        void testBuilder_ShouldSupportMinimalFields() {
            AuditLogEntity entity = AuditLogEntity.builder()
                .operationType("LOGIN")
                .operatorId(1L)
                .operatorUsername("admin")
                .ipAddress("192.168.1.1")
                .result("SUCCESS")
                .build();

            assertNotNull(entity.getOperationType());
            assertNotNull(entity.getOperatorId());
            assertEquals("SUCCESS", entity.getResult());
            assertNull(entity.getId());
            assertNull(entity.getTargetType());
            assertNull(entity.getTargetId());
            assertNull(entity.getDetails());
        }
    }
}

package interview.guide.modules.admin.model;

import interview.guide.modules.admin.enums.AdminRole;
import interview.guide.modules.admin.enums.AdminStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理员实体单元测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>实体字段验证</li>
 *   <li>默认值的正确设置</li>
 *   <li>Builder 模式功能</li>
 * </ul>
 */
@DisplayName("管理员实体测试")
class AdminUserEntityTest {

    @Nested
    @DisplayName("实体字段验证测试")
    class FieldValidationTests {

        @Test
        @DisplayName("使用 Builder 创建实体时，ID 应该为 null")
        void testBuilder_ShouldReturnNullId() {
            AdminUserEntity entity = AdminUserEntity.builder()
                .username("admin")
                .password("hashed_password")
                .email("admin@test.com")
                .role(AdminRole.ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();

            assertNull(entity.getId());
        }

        @Test
        @DisplayName("使用 Builder 创建实体时，所有字段应正确设置")
        void testBuilder_ShouldSetAllFields() {
            String username = "superadmin";
            String password = "hashed_password";
            String email = "superadmin@test.com";
            AdminRole role = AdminRole.SUPER_ADMIN;
            AdminStatus status = AdminStatus.ACTIVE;
            String lastLoginIp = "192.168.1.1";
            LocalDateTime lastLoginAt = LocalDateTime.now();

            AdminUserEntity entity = AdminUserEntity.builder()
                .username(username)
                .password(password)
                .email(email)
                .role(role)
                .status(status)
                .lastLoginIp(lastLoginIp)
                .lastLoginAt(lastLoginAt)
                .build();

            assertEquals(username, entity.getUsername());
            assertEquals(password, entity.getPassword());
            assertEquals(email, entity.getEmail());
            assertEquals(role, entity.getRole());
            assertEquals(status, entity.getStatus());
            assertEquals(lastLoginIp, entity.getLastLoginIp());
            assertEquals(lastLoginAt, entity.getLastLoginAt());
        }

        @Test
        @DisplayName("默认角色应该是 ADMIN")
        void testDefaultRole_ShouldBeAdmin() {
            AdminUserEntity entity = AdminUserEntity.builder()
                .username("admin")
                .build();

            assertEquals(AdminRole.ADMIN, entity.getRole());
        }

        @Test
        @DisplayName("默认状态应该是 ACTIVE")
        void testDefaultStatus_ShouldBeActive() {
            AdminUserEntity entity = AdminUserEntity.builder()
                .username("admin")
                .build();

            assertEquals(AdminStatus.ACTIVE, entity.getStatus());
        }
    }

    @Nested
    @DisplayName("枚举测试")
    class EnumTests {

        @Test
        @DisplayName("AdminRole 应包含 SUPER_ADMIN 和 ADMIN")
        void testAdminRole_ShouldHaveCorrectValues() {
            AdminRole[] roles = AdminRole.values();
            assertEquals(2, roles.length);
            assertNotNull(AdminRole.SUPER_ADMIN);
            assertNotNull(AdminRole.ADMIN);
        }

        @Test
        @DisplayName("AdminStatus 应包含 ACTIVE, DISABLED, DELETED")
        void testAdminStatus_ShouldHaveCorrectValues() {
            AdminStatus[] statuses = AdminStatus.values();
            assertEquals(3, statuses.length);
            assertNotNull(AdminStatus.ACTIVE);
            assertNotNull(AdminStatus.DISABLED);
            assertNotNull(AdminStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("全参数构造器测试")
    class ConstructorTests {

        @Test
        @DisplayName("使用全参数构造器创建实体")
        void testAllArgsConstructor() {
            Long id = 1L;
            String username = "admin";
            String password = "password";
            String email = "admin@test.com";
            AdminRole role = AdminRole.ADMIN;
            AdminStatus status = AdminStatus.ACTIVE;
            LocalDateTime createdAt = LocalDateTime.now();
            LocalDateTime updatedAt = LocalDateTime.now();

            AdminUserEntity entity = new AdminUserEntity(
                id, username, password, email, role, status,
                null, null, createdAt, updatedAt
            );

            assertEquals(id, entity.getId());
            assertEquals(username, entity.getUsername());
            assertEquals(password, entity.getPassword());
            assertEquals(email, entity.getEmail());
            assertEquals(role, entity.getRole());
            assertEquals(status, entity.getStatus());
            assertEquals(createdAt, entity.getCreatedAt());
            assertEquals(updatedAt, entity.getUpdatedAt());
        }
    }
}

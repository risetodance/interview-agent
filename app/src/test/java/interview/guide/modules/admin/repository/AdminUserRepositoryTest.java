package interview.guide.modules.admin.repository;

import interview.guide.modules.admin.enums.AdminRole;
import interview.guide.modules.admin.enums.AdminStatus;
import interview.guide.modules.admin.model.AdminUserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 管理员 Repository 单元测试（Mock 测试）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("管理员 Repository 测试")
class AdminUserRepositoryTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Nested
    @DisplayName("根据用户名查询管理员")
    class FindByUsernameTests {

        @Test
        @DisplayName("根据用户名查询应返回正确结果")
        void testFindByUsername() {
            // Given
            AdminUserEntity admin = AdminUserEntity.builder()
                .id(1L)
                .username("admin")
                .password("hashed")
                .email("admin@test.com")
                .role(AdminRole.ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            // When
            Optional<AdminUserEntity> result = adminUserRepository.findByUsername("admin");

            // Then
            assertTrue(result.isPresent());
            assertEquals("admin", result.get().getUsername());
            verify(adminUserRepository).findByUsername("admin");
        }

        @Test
        @DisplayName("根据不存在的用户名查询应返回空")
        void testFindByUsername_NotFound() {
            // Given
            when(adminUserRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            // When
            Optional<AdminUserEntity> result = adminUserRepository.findByUsername("nonexistent");

            // Then
            assertFalse(result.isPresent());
            verify(adminUserRepository).findByUsername("nonexistent");
        }
    }

    @Nested
    @DisplayName("根据邮箱查询管理员")
    class FindByEmailTests {

        @Test
        @DisplayName("根据邮箱查询应返回正确结果")
        void testFindByEmail() {
            // Given
            AdminUserEntity admin = AdminUserEntity.builder()
                .id(1L)
                .username("admin")
                .email("admin@test.com")
                .role(AdminRole.ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

            // When
            Optional<AdminUserEntity> result = adminUserRepository.findByEmail("admin@test.com");

            // Then
            assertTrue(result.isPresent());
            assertEquals("admin@test.com", result.get().getEmail());
        }
    }

    @Nested
    @DisplayName("检查用户名是否存在")
    class ExistsByUsernameTests {

        @Test
        @DisplayName("存在的用户名应返回 true")
        void testExistsByUsername_True() {
            when(adminUserRepository.existsByUsername("existing")).thenReturn(true);
            assertTrue(adminUserRepository.existsByUsername("existing"));
        }

        @Test
        @DisplayName("不存在的用户名应返回 false")
        void testExistsByUsername_False() {
            when(adminUserRepository.existsByUsername("nonexistent")).thenReturn(false);
            assertFalse(adminUserRepository.existsByUsername("nonexistent"));
        }
    }

    @Nested
    @DisplayName("根据角色查询管理员")
    class FindByRoleTests {

        @Test
        @DisplayName("根据角色查询应返回管理员列表")
        void testFindByRole() {
            // Given
            AdminUserEntity admin = AdminUserEntity.builder()
                .id(1L)
                .username("admin")
                .role(AdminRole.ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            AdminUserEntity superAdmin = AdminUserEntity.builder()
                .id(2L)
                .username("superadmin")
                .role(AdminRole.SUPER_ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            when(adminUserRepository.findByRole(AdminRole.ADMIN)).thenReturn(List.of(admin));

            // When
            List<AdminUserEntity> result = adminUserRepository.findByRole(AdminRole.ADMIN);

            // Then
            assertEquals(1, result.size());
            assertEquals("admin", result.get(0).getUsername());
        }
    }

    @Nested
    @DisplayName("根据状态查询管理员")
    class FindByStatusTests {

        @Test
        @DisplayName("根据状态查询应返回管理员列表")
        void testFindByStatus() {
            // Given
            AdminUserEntity admin = AdminUserEntity.builder()
                .id(1L)
                .username("admin")
                .status(AdminStatus.ACTIVE)
                .build();
            when(adminUserRepository.findByStatus(AdminStatus.ACTIVE)).thenReturn(List.of(admin));

            // When
            List<AdminUserEntity> result = adminUserRepository.findByStatus(AdminStatus.ACTIVE);

            // Then
            assertEquals(1, result.size());
            assertEquals(AdminStatus.ACTIVE, result.get(0).getStatus());
        }

        @Test
        @DisplayName("禁用的管理员应返回对应列表")
        void testFindByStatus_Disabled() {
            AdminUserEntity disabled = AdminUserEntity.builder()
                .id(1L)
                .username("disabled")
                .status(AdminStatus.DISABLED)
                .build();
            when(adminUserRepository.findByStatus(AdminStatus.DISABLED)).thenReturn(List.of(disabled));

            List<AdminUserEntity> result = adminUserRepository.findByStatus(AdminStatus.DISABLED);

            assertEquals(1, result.size());
            assertEquals(AdminStatus.DISABLED, result.get(0).getStatus());
        }
    }

    @Nested
    @DisplayName("CRUD 操作测试")
    class CrudTests {

        @Test
        @DisplayName("保存管理员应返回保存的实体")
        void testSave() {
            // Given
            AdminUserEntity admin = AdminUserEntity.builder()
                .username("newadmin")
                .password("hashed")
                .role(AdminRole.ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            AdminUserEntity saved = AdminUserEntity.builder()
                .id(1L)
                .username("newadmin")
                .password("hashed")
                .role(AdminRole.ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            when(adminUserRepository.save(admin)).thenReturn(saved);

            // When
            AdminUserEntity result = adminUserRepository.save(admin);

            // Then
            assertNotNull(result.getId());
            assertEquals("newadmin", result.getUsername());
            verify(adminUserRepository).save(admin);
        }

        @Test
        @DisplayName("根据 ID 查询管理员")
        void testFindById() {
            // Given
            AdminUserEntity admin = AdminUserEntity.builder()
                .id(1L)
                .username("admin")
                .role(AdminRole.ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            when(adminUserRepository.findById(1L)).thenReturn(Optional.of(admin));

            // When
            Optional<AdminUserEntity> result = adminUserRepository.findById(1L);

            // Then
            assertTrue(result.isPresent());
            assertEquals(1L, result.get().getId());
        }

        @Test
        @DisplayName("查询所有管理员")
        void testFindAll() {
            // Given
            AdminUserEntity admin1 = AdminUserEntity.builder()
                .id(1L)
                .username("admin1")
                .role(AdminRole.ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            AdminUserEntity admin2 = AdminUserEntity.builder()
                .id(2L)
                .username("admin2")
                .role(AdminRole.SUPER_ADMIN)
                .status(AdminStatus.ACTIVE)
                .build();
            when(adminUserRepository.findAll()).thenReturn(List.of(admin1, admin2));

            // When
            List<AdminUserEntity> result = adminUserRepository.findAll();

            // Then
            assertEquals(2, result.size());
        }
    }
}

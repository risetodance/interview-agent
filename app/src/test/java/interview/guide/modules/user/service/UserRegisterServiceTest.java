package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.user.dto.RegisterRequest;
import interview.guide.modules.user.dto.RegisterResponse;
import interview.guide.modules.user.model.MembershipType;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserRegisterService 单元测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>用户名已存在时抛出异常</li>
 *   <li>邮箱已存在时抛出异常</li>
 *   <li>有效请求注册成功</li>
 *   <li>验证密码被正确加密</li>
 * </ul>
 */
@DisplayName("用户注册服务测试")
class UserRegisterServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserRegisterService userRegisterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userRegisterService = new UserRegisterService(userRepository, passwordEncoder);
    }

    @Nested
    @DisplayName("注册失败场景测试")
    class RegisterFailureTests {

        @Test
        @DisplayName("用户名已存在时抛出异常")
        void testRegister_WhenUsernameExists_ThrowException() {
            // Given: 用户名已存在
            RegisterRequest request = new RegisterRequest(
                "existingUser",
                "password123",
                "newemail@test.com",
                "New User"
            );
            when(userRepository.existsByUsername("existingUser")).thenReturn(true);

            // When & Then
            BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userRegisterService.register(request)
            );

            assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), exception.getCode());
            verify(userRepository, never()).existsByEmail(anyString());
            verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("邮箱已存在时抛出异常")
        void testRegister_WhenEmailExists_ThrowException() {
            // Given: 用户名不存在，但邮箱已存在
            RegisterRequest request = new RegisterRequest(
                "newUser",
                "password123",
                "existingemail@test.com",
                "New User"
            );
            when(userRepository.existsByUsername("newUser")).thenReturn(false);
            when(userRepository.existsByEmail("existingemail@test.com")).thenReturn(true);

            // When & Then
            BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userRegisterService.register(request)
            );

            assertEquals(ErrorCode.EMAIL_EXISTS.getCode(), exception.getCode());
            verify(userRepository, never()).save(any(UserEntity.class));
        }
    }

    @Nested
    @DisplayName("注册成功场景测试")
    class RegisterSuccessTests {

        @Test
        @DisplayName("有效请求注册成功")
        void testRegister_WhenValidRequest_Success() {
            // Given: 有效的注册请求
            RegisterRequest request = new RegisterRequest(
                "newUser",
                "password123",
                "newemail@test.com",
                "New User"
            );

            when(userRepository.existsByUsername("newUser")).thenReturn(false);
            when(userRepository.existsByEmail("newemail@test.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encodedPassword123");

            // 创建保存后的用户实体
            UserEntity savedUser = UserEntity.builder()
                .id(1L)
                .username("newUser")
                .password("encodedPassword123")
                .email("newemail@test.com")
                .nickname("New User")
                .avatar(null)
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .points(0)
                .membership(MembershipType.FREE)
                .createdAt(LocalDateTime.now())
                .build();

            when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

            // When
            RegisterResponse response = userRegisterService.register(request);

            // Then
            assertNotNull(response);
            assertEquals(1L, response.id());
            assertEquals("newUser", response.username());
            assertEquals("newemail@test.com", response.email());
            assertEquals("New User", response.nickname());
            assertEquals(UserStatus.ACTIVE, response.status());
            assertEquals(UserRole.USER, response.role());

            verify(userRepository, times(1)).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("验证密码被正确加密")
        void testRegister_EncryptPassword() {
            // Given: 有效的注册请求
            RegisterRequest request = new RegisterRequest(
                "testUser",
                "myPassword",
                "test@example.com",
                "Test User"
            );

            when(userRepository.existsByUsername("testUser")).thenReturn(false);
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("myPassword")).thenReturn("bcryptEncodedPassword");

            // 创建保存后的用户实体
            UserEntity savedUser = UserEntity.builder()
                .id(2L)
                .username("testUser")
                .password("bcryptEncodedPassword")
                .email("test@example.com")
                .nickname("Test User")
                .avatar(null)
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .points(0)
                .membership(MembershipType.FREE)
                .createdAt(LocalDateTime.now())
                .build();

            when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

            // When
            userRegisterService.register(request);

            // Then: 验证密码加密被调用
            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(userCaptor.capture());

            UserEntity capturedUser = userCaptor.getValue();
            assertEquals("bcryptEncodedPassword", capturedUser.getPassword());
            verify(passwordEncoder, times(1)).encode("myPassword");
        }

        @Test
        @DisplayName("昵称为空时使用用户名作为昵称")
        void testRegister_WhenNicknameNull_UsesUsernameAsNickname() {
            // Given: 昵称为空的注册请求
            RegisterRequest request = new RegisterRequest(
                "userWithoutNick",
                "password123",
                "test@test.com",
                null  // nickname 为 null
            );

            when(userRepository.existsByUsername("userWithoutNick")).thenReturn(false);
            when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");

            // 创建保存后的用户实体
            UserEntity savedUser = UserEntity.builder()
                .id(3L)
                .username("userWithoutNick")
                .password("encodedPassword")
                .email("test@test.com")
                .nickname("userWithoutNick")  // 期望使用用户名作为昵称
                .avatar(null)
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .points(0)
                .membership(MembershipType.FREE)
                .createdAt(LocalDateTime.now())
                .build();

            when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

            // When
            RegisterResponse response = userRegisterService.register(request);

            // Then
            assertNotNull(response);
            assertEquals("userWithoutNick", response.nickname());
        }
    }
}

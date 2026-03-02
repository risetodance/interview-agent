package interview.guide.modules.user.service;

import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.user.dto.LoginRequest;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.model.MembershipType;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserLoginService 单元测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>用户不存在时抛出异常</li>
 *   <li>密码错误时抛出异常</li>
 *   <li>用户被禁用时抛出异常</li>
 *   <li>有效凭证登录成功</li>
 * </ul>
 */
@DisplayName("用户登录服务测试")
class UserLoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private UserLoginService userLoginService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userLoginService = new UserLoginService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Nested
    @DisplayName("登录失败场景测试")
    class LoginFailureTests {

        @Test
        @DisplayName("用户不存在时抛出异常")
        void testLogin_WhenUserNotFound_ThrowException() {
            // Given: 不存在的用户名
            LoginRequest request = new LoginRequest("nonExistentUser", "password123");
            when(userRepository.findByUsername("nonExistentUser")).thenReturn(Optional.empty());

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userLoginService.login(request)
            );

            assertEquals("用户名或密码错误", exception.getMessage());
            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(jwtTokenProvider, never()).generateToken(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("密码错误时抛出异常")
        void testLogin_WhenPasswordWrong_ThrowException() {
            // Given: 存在的用户但密码错误
            LoginRequest request = new LoginRequest("existingUser", "wrongPassword");

            UserEntity user = UserEntity.builder()
                .id(1L)
                .username("existingUser")
                .password("encodedCorrectPassword")
                .email("user@test.com")
                .nickname("Existing User")
                .avatar(null)
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .points(0)
                .membership(MembershipType.FREE)
                .createdAt(LocalDateTime.now())
                .build();

            when(userRepository.findByUsername("existingUser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPassword", "encodedCorrectPassword")).thenReturn(false);

            // When & Then
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userLoginService.login(request)
            );

            assertEquals("用户名或密码错误", exception.getMessage());
            verify(jwtTokenProvider, never()).generateToken(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("用户被禁用时抛出异常")
        void testLogin_WhenUserDisabled_ThrowException() {
            // Given: 用户状态为 BANNED
            LoginRequest request = new LoginRequest("bannedUser", "correctPassword");

            UserEntity user = UserEntity.builder()
                .id(1L)
                .username("bannedUser")
                .password("encodedCorrectPassword")
                .email("banned@test.com")
                .nickname("Banned User")
                .avatar(null)
                .status(UserStatus.BANNED)  // 禁用状态
                .role(UserRole.USER)
                .points(0)
                .membership(MembershipType.FREE)
                .createdAt(LocalDateTime.now())
                .build();

            when(userRepository.findByUsername("bannedUser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correctPassword", "encodedCorrectPassword")).thenReturn(true);

            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> userLoginService.login(request)
            );

            assertEquals("用户账号状态异常，请联系管理员", exception.getMessage());
            verify(jwtTokenProvider, never()).generateToken(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("用户未激活时抛出异常")
        void testLogin_WhenUserInactive_ThrowException() {
            // Given: 用户状态为 INACTIVE
            LoginRequest request = new LoginRequest("inactiveUser", "correctPassword");

            UserEntity user = UserEntity.builder()
                .id(1L)
                .username("inactiveUser")
                .password("encodedCorrectPassword")
                .email("inactive@test.com")
                .nickname("Inactive User")
                .avatar(null)
                .status(UserStatus.INACTIVE)  // 未激活状态
                .role(UserRole.USER)
                .points(0)
                .membership(MembershipType.FREE)
                .createdAt(LocalDateTime.now())
                .build();

            when(userRepository.findByUsername("inactiveUser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correctPassword", "encodedCorrectPassword")).thenReturn(true);

            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> userLoginService.login(request)
            );

            assertEquals("用户账号状态异常，请联系管理员", exception.getMessage());
            verify(jwtTokenProvider, never()).generateToken(anyLong(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("登录成功场景测试")
    class LoginSuccessTests {

        @Test
        @DisplayName("有效凭证登录成功")
        void testLogin_WhenValidCredentials_Success() {
            // Given: 有效的登录凭证
            LoginRequest request = new LoginRequest("validUser", "correctPassword");

            UserEntity user = UserEntity.builder()
                .id(1L)
                .username("validUser")
                .password("encodedCorrectPassword")
                .email("valid@test.com")
                .nickname("Valid User")
                .avatar(null)
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .points(100)
                .membership(MembershipType.FREE)
                .createdAt(LocalDateTime.now())
                .build();

            when(userRepository.findByUsername("validUser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correctPassword", "encodedCorrectPassword")).thenReturn(true);
            when(jwtTokenProvider.generateToken(1L, "validUser", "USER"))
                .thenReturn("jwt-token-abc123");

            // When
            LoginResponse response = userLoginService.login(request);

            // Then
            assertNotNull(response);
            assertEquals("jwt-token-abc123", response.token());
            assertEquals(1L, response.userId());
            assertEquals("validUser", response.username());
            assertEquals("USER", response.role());

            verify(jwtTokenProvider, times(1)).generateToken(1L, "validUser", "USER");
        }

        @Test
        @DisplayName("管理员用户登录成功")
        void testLogin_WhenAdminUser_Success() {
            // Given: 管理员用户登录
            LoginRequest request = new LoginRequest("adminUser", "adminPassword");

            UserEntity user = UserEntity.builder()
                .id(99L)
                .username("adminUser")
                .password("encodedAdminPassword")
                .email("admin@test.com")
                .nickname("Admin")
                .avatar(null)
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)  // 管理员角色
                .points(0)
                .membership(MembershipType.PREMIUM)
                .createdAt(LocalDateTime.now())
                .build();

            when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("adminPassword", "encodedAdminPassword")).thenReturn(true);
            when(jwtTokenProvider.generateToken(99L, "adminUser", "ADMIN"))
                .thenReturn("jwt-token-admin");

            // When
            LoginResponse response = userLoginService.login(request);

            // Then
            assertNotNull(response);
            assertEquals("jwt-token-admin", response.token());
            assertEquals(99L, response.userId());
            assertEquals("adminUser", response.username());
            assertEquals("ADMIN", response.role());

            verify(jwtTokenProvider, times(1)).generateToken(99L, "adminUser", "ADMIN");
        }
    }
}

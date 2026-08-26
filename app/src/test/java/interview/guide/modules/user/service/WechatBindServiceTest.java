package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.WechatAuthDTOs.WechatBindByEmailRequest;
import interview.guide.modules.user.dto.WechatAuthDTOs.WechatBindByPasswordRequest;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.model.UserWechatBinding;
import interview.guide.modules.user.model.WechatChannel;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.repository.UserWechatBindingRepository;
import interview.guide.modules.user.service.WechatBindTicketService.TicketPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * WechatBindService 单元测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>密码通道：成功绑定 / 账号或密码错误计失败 / 票据无效</li>
 *   <li>验证码通道：成功绑定 / 验证码错误计失败 / 邮箱未注册还原验证码</li>
 *   <li>公共收尾：微信已绑其他账号报错、账号已绑其他微信报错、禁用账号拒绝</li>
 * </ul>
 */
@DisplayName("微信绑定服务测试")
class WechatBindServiceTest {

    @Mock
    private WechatBindTicketService bindTicketService;
    @Mock
    private EmailCodeService emailCodeService;
    @Mock
    private UserWechatBindingRepository wechatBindingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private WechatBindService bindService;

    private static final String TICKET = "ticket-abc";
    private static final TicketPayload PAYLOAD = new TicketPayload("openid-123", null);

    private UserEntity activeUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bindService = new WechatBindService(bindTicketService, emailCodeService,
                wechatBindingRepository, userRepository, passwordEncoder, jwtTokenProvider);

        activeUser = UserEntity.builder()
                .id(1L)
                .username("payton")
                .password("$bcrypt$hashed")
                .email("payton@example.com")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();

        // 公共桩：票据有效、无绑定冲突、JWT 正常签发
        when(bindTicketService.peek(TICKET)).thenReturn(PAYLOAD);
        when(wechatBindingRepository.findByChannelAndOpenid(WechatChannel.MINIAPP, "openid-123"))
                .thenReturn(Optional.empty());
        when(wechatBindingRepository.findByUserIdAndChannel(1L, WechatChannel.MINIAPP))
                .thenReturn(Optional.empty());
        when(jwtTokenProvider.generateToken(eq(1L), eq("payton"), eq("USER")))
                .thenReturn("jwt-token");
    }

    @Nested
    @DisplayName("密码通道测试")
    class PasswordChannelTests {

        private WechatBindByPasswordRequest request(String account, String password) {
            return new WechatBindByPasswordRequest(TICKET, account, password);
        }

        @Test
        @DisplayName("用户名+正确密码：消费票据、写入绑定、返回登录态")
        void testBindByPassword_Success() {
            when(userRepository.findByUsername("payton")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("raw-pass", "$bcrypt$hashed")).thenReturn(true);

            LoginResponse response = bindService.bindByPassword(request("payton", "raw-pass"));

            assertEquals("jwt-token", response.token());
            assertEquals(1L, response.userId());
            verify(bindTicketService).invalidate(TICKET);
            verify(wechatBindingRepository).save(any(UserWechatBinding.class));
        }

        @Test
        @DisplayName("邮箱（规范化）+ 正确密码：同样定位到用户")
        void testBindByPassword_EmailAccount_Success() {
            when(userRepository.findByUsername("Payton@Example.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("payton@example.com")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("raw-pass", "$bcrypt$hashed")).thenReturn(true);

            LoginResponse response = bindService.bindByPassword(request("Payton@Example.com", "raw-pass"));

            assertEquals("jwt-token", response.token());
            verify(wechatBindingRepository).save(any(UserWechatBinding.class));
        }

        @Test
        @DisplayName("账号不存在：统一报账号或密码错误并计一次票据失败")
        void testBindByPassword_UserNotFound_RecordFailure() {
            when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> bindService.bindByPassword(request("nobody", "whatever")));

            assertEquals(ErrorCode.INVALID_PASSWORD.getCode(), exception.getCode());
            verify(bindTicketService).recordFailure(TICKET);
            verify(bindTicketService, never()).invalidate(anyString());
            verify(wechatBindingRepository, never()).save(any());
        }

        @Test
        @DisplayName("密码错误：报错并计一次票据失败，不落绑定")
        void testBindByPassword_WrongPassword_RecordFailure() {
            when(userRepository.findByUsername("payton")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrong", "$bcrypt$hashed")).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> bindService.bindByPassword(request("payton", "wrong")));

            assertEquals(ErrorCode.INVALID_PASSWORD.getCode(), exception.getCode());
            verify(bindTicketService).recordFailure(TICKET);
            verify(wechatBindingRepository, never()).save(any());
        }

        @Test
        @DisplayName("票据无效：直接抛票据异常，不查用户")
        void testBindByPassword_InvalidTicket_ThrowImmediately() {
            when(bindTicketService.peek("bad-ticket"))
                    .thenThrow(new BusinessException(ErrorCode.WECHAT_TICKET_INVALID));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> bindService.bindByPassword(new WechatBindByPasswordRequest("bad-ticket", "a", "b")));

            assertEquals(ErrorCode.WECHAT_TICKET_INVALID.getCode(), exception.getCode());
            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    @DisplayName("验证码通道测试")
    class EmailCodeChannelTests {

        private WechatBindByEmailRequest request(String email, String code) {
            return new WechatBindByEmailRequest(TICKET, email, code);
        }

        @Test
        @DisplayName("邮箱+正确验证码：绑定成功返回登录态")
        void testBindByEmailCode_Success() {
            when(emailCodeService.verifyCode(eq("payton@example.com"), eq(EmailCodeService.Scene.LOGIN), eq("123456")))
                    .thenReturn("123456");
            when(userRepository.findByEmail("payton@example.com")).thenReturn(Optional.of(activeUser));

            LoginResponse response = bindService.bindByEmailCode(request("payton@example.com", "123456"));

            assertEquals("jwt-token", response.token());
            verify(bindTicketService).invalidate(TICKET);
            verify(wechatBindingRepository).save(any(UserWechatBinding.class));
        }

        @Test
        @DisplayName("验证码错误：原异常透传并计一次票据失败")
        void testBindByEmailCode_WrongCode_RecordFailure() {
            when(emailCodeService.verifyCode(anyString(), any(), anyString()))
                    .thenThrow(new BusinessException(ErrorCode.EMAIL_CODE_INVALID));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> bindService.bindByEmailCode(request("payton@example.com", "000000")));

            assertEquals(ErrorCode.EMAIL_CODE_INVALID.getCode(), exception.getCode());
            verify(bindTicketService).recordFailure(TICKET);
            verify(wechatBindingRepository, never()).save(any());
        }

        @Test
        @DisplayName("邮箱未注册：还原验证码并提示先注册")
        void testBindByEmailCode_EmailNotRegistered_RestoreCode() {
            when(emailCodeService.verifyCode(eq("new@example.com"), eq(EmailCodeService.Scene.LOGIN), eq("123456")))
                    .thenReturn("123456");
            when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> bindService.bindByEmailCode(request("new@example.com", "123456")));

            assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), exception.getCode());
            verify(emailCodeService).restoreCode("new@example.com", EmailCodeService.Scene.LOGIN, "123456");
            verify(wechatBindingRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("公共冲突与状态测试")
    class ConflictAndStatusTests {

        private void loginAsActiveUser() {
            when(userRepository.findByUsername("payton")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("raw-pass", "$bcrypt$hashed")).thenReturn(true);
        }

        @Test
        @DisplayName("该微信已绑在其他账号：报错拒绝，不落新绑定")
        void testBind_ConflictWeChat_Throw() {
            loginAsActiveUser();
            when(wechatBindingRepository.findByChannelAndOpenid(WechatChannel.MINIAPP, "openid-123"))
                    .thenReturn(Optional.of(UserWechatBinding.builder().userId(99L).build()));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> bindService.bindByPassword(new WechatBindByPasswordRequest(TICKET, "payton", "raw-pass")));

            assertEquals(ErrorCode.WECHAT_BIND_CONFLICT_WECHAT.getCode(), exception.getCode());
            verify(wechatBindingRepository, never()).save(any());
        }

        @Test
        @DisplayName("该账号已绑其他微信：报错拒绝")
        void testBind_ConflictAccount_Throw() {
            loginAsActiveUser();
            when(wechatBindingRepository.findByUserIdAndChannel(1L, WechatChannel.MINIAPP))
                    .thenReturn(Optional.of(UserWechatBinding.builder().userId(1L).build()));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> bindService.bindByPassword(new WechatBindByPasswordRequest(TICKET, "payton", "raw-pass")));

            assertEquals(ErrorCode.WECHAT_BIND_CONFLICT_ACCOUNT.getCode(), exception.getCode());
            verify(wechatBindingRepository, never()).save(any());
        }

        @Test
        @DisplayName("账号被禁用：拒绝绑定")
        void testBind_UserDisabled_Throw() {
            activeUser.setStatus(UserStatus.BANNED);
            loginAsActiveUser();

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> bindService.bindByPassword(new WechatBindByPasswordRequest(TICKET, "payton", "raw-pass")));

            assertEquals(ErrorCode.USER_DISABLED.getCode(), exception.getCode());
            verify(wechatBindingRepository, never()).save(any());
        }
    }
}

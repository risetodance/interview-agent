package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.user.dto.EmailAuthDTOs.EmailLoginResponse;
import interview.guide.modules.user.dto.EmailAuthDTOs.EmailRegisterRequest;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.RegisterRequest;
import interview.guide.modules.user.dto.RegisterResponse;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.service.EmailCodeService.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮箱验证码认证服务
 * 覆盖：验证码发送、两步式邮箱登录/注册、忘记密码重置、邮箱改密、绑定/换绑邮箱
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private final EmailCodeService emailCodeService;
    private final EmailSendService emailSendService;
    private final UserRegisterService userRegisterService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 发送验证码
     * 重置密码场景要求邮箱已注册（避免向任意邮箱发信）；登录场景允许未注册（走两步式注册）；
     * 注册场景仅对未注册邮箱发码，避免用户填完表单提交才发现邮箱冲突
     */
    public void sendCode(String email, String sceneStr) {
        email = EmailNormalizer.normalize(email);
        Scene scene = Scene.fromString(sceneStr);
        if (scene == Scene.RESET_PASSWORD && !userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "该邮箱未注册");
        }
        if (scene == Scene.REGISTER && userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
        emailCodeService.sendCode(email, scene);
    }

    /**
     * 邮箱验证码登录（两步式第一步）
     * 验证码校验通过后：
     * - 已注册 → 直接登录返回 token
     * - 未注册 → 还原验证码，返回 needsRegister=true，前端进入第二步设置用户名密码
     */
    public EmailLoginResponse loginByEmail(String email, String code) {
        // S4：规范化后用 final 局部变量，供下方 lambda 捕获
        final String normalizedEmail = EmailNormalizer.normalize(email);
        String verifiedCode = emailCodeService.verifyCode(normalizedEmail, Scene.LOGIN, code);

        return userRepository.findByEmail(normalizedEmail)
                .<EmailLoginResponse>map(this::buildLoginSuccess)
                .orElseGet(() -> {
                    // 未注册：把码还原给第二步 register 消费，用户无需重新收码
                    emailCodeService.restoreCode(normalizedEmail, Scene.LOGIN, verifiedCode);
                    log.info("邮箱未注册，进入两步式注册第二步: email={}", normalizedEmail);
                    return new EmailLoginResponse(true, null);
                });
    }

    /**
     * 邮箱验证码注册（两步式第二步）：校验码 → 建号 → 直接登录
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse registerByEmail(EmailRegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());

        // F1：用户名/邮箱冲突预检前移到验证码消费之前，冲突时直接失败，避免白白消耗验证码
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 消费第一步还原的验证码（码失效需重新走第一步收码）
        String verifiedCode = emailCodeService.verifyCode(email, Scene.LOGIN, request.code());

        RegisterResponse resp;
        try {
            resp = userRegisterService.register(new RegisterRequest(
                    request.username(),
                    request.password(),
                    email,
                    // 两步式已用 LOGIN 码验证过邮箱所有权，无需重复校验，code 传 null
                    null,
                    request.nickname() != null ? request.nickname() : request.username()
            ));
        } catch (RuntimeException e) {
            // F1：register 内其他校验失败时退回验证码，用户无需重新收码；随后原样重抛
            emailCodeService.restoreCode(email, Scene.LOGIN, verifiedCode);
            throw e;
        }

        // SIM-7：注册服务已返回完整用户信息（id/username/role），直接据此签发登录态，
        // 省一次注册成功后按邮箱回查的 SELECT；新注册用户状态必为 ACTIVE（register 已保证），
        // 故无需再走 buildLoginResponse 的状态检查
        log.info("邮箱验证码注册成功: userId={}, username={}", resp.id(), resp.username());
        return new LoginResponse(
                jwtTokenProvider.generateToken(resp.id(), resp.username(), resp.role().name()),
                resp.id(),
                resp.username(),
                resp.role().name()
        );
    }

    /**
     * 忘记密码：凭邮箱验证码重置密码（未登录）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String email, String code, String newPassword) {
        email = EmailNormalizer.normalize(email);
        emailCodeService.verifyCode(email, Scene.RESET_PASSWORD, code);

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("密码重置成功: userId={}", user.getId());
    }

    /**
     * 登录态修改密码（邮箱验证码方式）
     * 验证码必须发往当前账号绑定的邮箱，防止用他人邮箱的码改自己的密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePasswordByEmail(Long userId, String code, String newPassword) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_CONFIGURED, "当前账号未绑定邮箱");
        }
        // 验码必须与发码入口使用同一份规范化邮箱拼 Redis key：
        // DB 原值可能混合大小写，直接使用会导致 key 漂移、误报“验证码已过期”
        emailCodeService.verifyCode(EmailNormalizer.normalize(user.getEmail()), Scene.CHANGE_PASSWORD, code);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("邮箱验证码修改密码成功: userId={}", userId);
    }

    /**
     * 绑定/换绑邮箱：验证新邮箱后绑定到当前账号（新邮箱单验证）
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindEmail(Long userId, String newEmail, String code) {
        newEmail = EmailNormalizer.normalize(newEmail);
        // S2：先验码后占用检查——未持有新邮箱验证码的请求无法探测邮箱是否已被占用（堵无凭证枚举）
        emailCodeService.verifyCode(newEmail, Scene.BIND_EMAIL, code);
        if (userRepository.existsByEmail(newEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        userRepository.save(user);
        log.info("邮箱绑定成功: userId={}, oldEmail={}, newEmail={}", userId, oldEmail, newEmail);
        notifyOldEmailChanged(oldEmail, newEmail);
    }

    /**
     * S1 缓解：换绑成功后向旧邮箱发送变更通知，让原邮箱主人有机会发现异常换绑。
     * 完整防护需旧邮箱二次验证或密码确认；本通知为不改变现有交互的最小缓解，失败仅告警、不回滚主流程。
     */
    private void notifyOldEmailChanged(String oldEmail, String newEmail) {
        if (oldEmail == null || oldEmail.isBlank()) {
            return;
        }
        try {
            emailSendService.sendEmailChangedNotification(oldEmail, newEmail);
        } catch (Exception e) {
            log.warn("旧邮箱变更通知发送失败（不影响换绑结果）: oldEmail={}, error={}", oldEmail, e.getMessage());
        }
    }

    /**
     * 构建登录成功响应（外层包装：needsRegister=false + LoginResponse）
     */
    private EmailLoginResponse buildLoginSuccess(UserEntity user) {
        return new EmailLoginResponse(false, buildLoginResponse(user));
    }

    /**
     * 构建登录态响应（状态检查 + JWT 生成）
     */
    private LoginResponse buildLoginResponse(UserEntity user) {
        UserStatus status = user.getStatus();
        if (status != null && status != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "用户账号状态异常，请联系管理员");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }
}

package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.user.dto.RegisterRequest;
import interview.guide.modules.user.dto.RegisterResponse;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户注册服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegisterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户注册
     * 1. 验证用户名和邮箱是否已存在
     * 2. 使用 BCrypt 加密密码
     * 3. 创建新用户并保存
     * 4. 返回注册结果（用户信息，不包含密码）
     *
     * @param request 注册请求
     * @return 注册响应（用户信息，不包含密码）
     */
    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(RegisterRequest request) {
        // V2：邮箱入口规范化（trim + 统一小写），统一走 EmailNormalizer，与认证链路共用同一份实现。
        // 若原样入库混合大小写邮箱，发码/登录/忘记密码/占用检查均按小写查询与拼 Redis key，会产生漂移
        // （改密误报验证码已过期、忘记密码误报未注册、同邮箱不同大小写重复建号）
        request = normalizeRequestEmail(request);

        // 1. 验证用户名是否已存在
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 2. 验证邮箱是否已存在
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }

        // 3. 创建新用户实体（使用 immutable 模式，创建新对象）
        UserEntity user = createUserEntity(request);

        // 4. 保存用户
        UserEntity savedUser = userRepository.save(user);
        log.info("用户注册成功: id={}, username={}", savedUser.getId(), savedUser.getUsername());

        // 5. 返回注册结果（不包含密码）
        return toRegisterResponse(savedUser);
    }

    /**
     * 返回邮箱已规范化（trim + 小写）的新请求对象
     * 规范化逻辑统一走 EmailNormalizer（与认证链路共用同一份实现）
     * 遵循 immutable 原则创建新对象，不修改原对象；已是规范格式时直接原样返回
     */
    private RegisterRequest normalizeRequestEmail(RegisterRequest request) {
        String email = request.email();
        if (email == null) {
            return request;
        }
        String normalized = EmailNormalizer.normalize(email);
        return normalized.equals(email) ? request : new RegisterRequest(
                request.username(),
                request.password(),
                normalized,
                // 原样保留验证码字段（本方法仅规范化邮箱，不改变其余字段）
                request.code(),
                request.nickname()
        );
    }

    /**
     * 创建用户实体
     * 遵循 immutable 原则，创建新对象而非修改现有对象
     */
    private UserEntity createUserEntity(RegisterRequest request) {
        // 使用 Builder 模式创建新对象，设置默认值
        return UserEntity.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .nickname(request.nickname() != null ? request.nickname() : request.username())
                .status(interview.guide.modules.user.model.UserStatus.ACTIVE)
                .role(interview.guide.modules.user.model.UserRole.USER)
                .points(0)
                .membership(interview.guide.modules.user.model.MembershipType.FREE)
                .build();
    }

    /**
     * 将实体转换为注册响应 DTO
     * 遵循 immutable 原则，返回新对象
     */
    private RegisterResponse toRegisterResponse(UserEntity user) {
        return new RegisterResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatar(),
                user.getStatus(),
                user.getRole(),
                user.getPoints(),
                user.getMembership(),
                user.getCreatedAt()
        );
    }
}

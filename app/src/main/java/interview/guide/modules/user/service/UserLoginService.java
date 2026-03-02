package interview.guide.modules.user.service;

import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.user.dto.LoginRequest;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户登录服务
 */
@Service
public class UserLoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserLoginService(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录结果
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 根据用户名查询用户
        Optional<UserEntity> userOpt = userRepository.findByUsername(request.username());
        UserEntity user = userOpt.orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        // 2. 验证密码是否正确
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 3. 检查用户状态是否正常（null 默认为 ACTIVE）
        UserStatus status = user.getStatus();
        if (status != null && status != UserStatus.ACTIVE) {
            throw new IllegalStateException("用户账号状态异常，请联系管理员");
        }

        // 4. 生成 JWT token
        String token = jwtTokenProvider.generateToken(
            user.getId(),
            user.getUsername(),
            user.getRole().name()
        );

        // 5. 返回登录结果
        return new LoginResponse(
            token,
            user.getId(),
            user.getUsername(),
            user.getRole().name()
        );
    }
}

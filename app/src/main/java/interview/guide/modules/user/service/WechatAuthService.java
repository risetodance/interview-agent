package interview.guide.modules.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.WechatLoginRequest;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 微信授权服务
 * 处理微信小程序登录相关的业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatAuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.wechat.miniapp.appid:}")
    private String miniAppAppid;

    @Value("${app.wechat.miniapp.secret:}")
    private String miniAppSecret;

    /**
     * 微信登录
     *
     * @param request 微信登录请求
     * @return 登录响应（包含JWT token）
     */
    @Transactional
    public LoginResponse wechatLogin(WechatLoginRequest request) {
        log.info("收到微信小程序登录请求");

        // 1. 通过code获取openid
        String openid = getOpenidFromWechat(request.code());
        if (openid == null || openid.isEmpty()) {
            throw new IllegalArgumentException("微信授权失败，无法获取用户标识");
        }

        log.info("成功获取微信openid: {}", openid);

        // 2. 根据openid查找或创建用户
        UserEntity user = findOrCreateWechatUser(openid);

        // 3. 检查用户状态
        UserStatus status = user.getStatus();
        if (status != null && status != UserStatus.ACTIVE) {
            throw new IllegalStateException("用户账号状态异常，请联系管理员");
        }

        // 4. 生成JWT token
        String token = jwtTokenProvider.generateToken(
            user.getId(),
            user.getUsername(),
            user.getRole().name()
        );

        log.info("微信用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 5. 返回登录结果
        return new LoginResponse(
            token,
            user.getId(),
            user.getUsername(),
            user.getRole().name()
        );
    }

    /**
     * 通过微信code换取openid
     *
     * @param code 微信授权码
     * @return openid，如果失败返回null
     */
    private String getOpenidFromWechat(String code) {
        String url = String.format(
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
            miniAppAppid,
            miniAppSecret,
            code
        );

        try {
            String response = restTemplate.getForObject(url, String.class);
            log.debug("微信API响应: {}", response);

            JsonNode jsonNode = objectMapper.readTree(response);

            // 检查是否有错误
            if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() != 0) {
                String errorMsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "未知错误";
                log.error("微信API错误: errcode={}, errmsg={}", jsonNode.get("errcode").asInt(), errorMsg);
                return null;
            }

            return jsonNode.has("openid") ? jsonNode.get("openid").asText() : null;

        } catch (Exception e) {
            log.error("调用微信API失败", e);
            return null;
        }
    }

    /**
     * 根据openid查找用户，如果不存在则创建新用户
     *
     * @param openid 微信openid
     * @return 用户实体
     */
    private UserEntity findOrCreateWechatUser(String openid) {
        // 1. 尝试查找已存在的用户
        Optional<UserEntity> existingUser = userRepository.findByWechatOpenid(openid);
        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        // 2. 创建新用户
        log.info("创建新的微信用户: openid={}", openid);

        // 生成唯一的用户名（使用UUID避免冲突）
        String username = "wechat_" + UUID.randomUUID().toString().substring(0, 8);

        UserEntity newUser = UserEntity.builder()
            .username(username)
            .wechatOpenid(openid)
            .nickname("微信用户")
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .points(0)
            .build();

        return userRepository.save(newUser);
    }
}

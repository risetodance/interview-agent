package interview.guide.modules.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserRole;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.model.UserWechatBinding;
import interview.guide.modules.user.model.WechatChannel;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.repository.UserWechatBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * 微信网页扫码登录服务
 * 处理微信网页应用（Web应用）的扫码登录业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatScanLoginService {

    private final UserRepository userRepository;
    private final UserWechatBindingRepository wechatBindingRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.wechat.web.appid:}")
    private String webAppid;

    @Value("${app.wechat.web.secret:}")
    private String webSecret;

    /**
     * 微信网页扫码登录
     *
     * @param code 微信授权码
     * @return 登录响应（包含JWT token）
     */
    @Transactional
    public LoginResponse scanLogin(String code) {
        log.info("收到微信网页扫码登录请求");

        // 1. 通过code获取openid（网页应用与小程序的openid不同）
        String openid = getOpenidFromWechat(code);
        if (openid == null || openid.isEmpty()) {
            throw new IllegalArgumentException("微信授权失败，无法获取用户标识");
        }

        log.info("成功获取微信网页openid: {}", openid);

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

        log.info("微信网页用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 5. 返回登录结果
        return new LoginResponse(
            token,
            user.getId(),
            user.getUsername(),
            user.getRole().name()
        );
    }

    /**
     * 通过微信code换取openid（网页应用API）
     *
     * @param code 微信授权码
     * @return openid，如果失败返回null
     */
    private String getOpenidFromWechat(String code) {
        String url = String.format(
            "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
            webAppid,
            webSecret,
            code
        );

        try {
            String response = restTemplate.getForObject(url, String.class);
            log.debug("微信网页API响应: {}", response);

            JsonNode jsonNode = objectMapper.readTree(response);

            // 检查是否有错误
            if (jsonNode.has("errcode") && jsonNode.get("errcode").asInt() != 0) {
                String errorMsg = jsonNode.has("errmsg") ? jsonNode.get("errmsg").asText() : "未知错误";
                log.error("微信网页API错误: errcode={}, errmsg={}", jsonNode.get("errcode").asInt(), errorMsg);
                return null;
            }

            return jsonNode.has("openid") ? jsonNode.get("openid").asText() : null;

        } catch (Exception e) {
            log.error("调用微信网页API失败", e);
            return null;
        }
    }

    /**
     * 根据openid查找用户，如果不存在则创建新用户
     * <p>
     * 注意：扫码登录保持"未绑定自动建号"的原有行为（本次改造仅将存储从 users.wechat_openid 列
     * 迁移到独立绑定表 channel=WEB_SCAN），后续如需统一为账号关联模式再另行改造
     *
     * @param openid 微信openid（网页应用，与小程序 openid 不同）
     * @return 用户实体
     */
    private UserEntity findOrCreateWechatUser(String openid) {
        // 1. 按绑定表查找已存在的用户
        Optional<UserWechatBinding> binding = wechatBindingRepository
                .findByChannelAndOpenid(WechatChannel.WEB_SCAN, openid);
        if (binding.isPresent()) {
            return userRepository.findById(binding.get().getUserId())
                    .orElseThrow(() -> new IllegalStateException("微信网页用户绑定数据异常，请重新登录"));
        }

        // 2. 创建新用户并写入绑定表
        log.info("创建新的微信网页用户: openid={}", openid);

        // 生成唯一的用户名（使用UUID避免冲突）
        String username = "wechat_web_" + UUID.randomUUID().toString().substring(0, 8);

        UserEntity newUser = UserEntity.builder()
            .username(username)
            // 本路径不落库 email（保持 null），无用户邮箱输入；后续用户经 bindEmail 绑定（入口已做小写规范化），故无需规范化
            .nickname("微信用户")
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .points(0)
            .build();
        UserEntity saved = userRepository.save(newUser);

        wechatBindingRepository.save(UserWechatBinding.builder()
                .userId(saved.getId())
                .channel(WechatChannel.WEB_SCAN)
                .openid(openid)
                .build());

        return saved;
    }
}

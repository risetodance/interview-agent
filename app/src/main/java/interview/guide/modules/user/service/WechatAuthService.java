package interview.guide.modules.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.WechatAuthDTOs.WechatLoginResponse;
import interview.guide.modules.user.dto.WechatLoginRequest;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.model.UserWechatBinding;
import interview.guide.modules.user.model.WechatChannel;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.repository.UserWechatBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * 微信授权服务
 * 处理微信小程序登录相关的业务逻辑
 * <p>
 * 登录策略（账号关联模式）：
 * - openid 已绑定账号 → 直接签发该账号的 JWT，无缝登录
 * - openid 未绑定 → 不自动建号，openid 暂存 Redis 并签发 5 分钟一次性票据，
 *   前端凭 ticket 进入关联账号页输 Web 端凭证完成绑定（见 WechatBindService）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatAuthService {

    private final UserRepository userRepository;
    private final UserWechatBindingRepository wechatBindingRepository;
    private final WechatBindTicketService bindTicketService;
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
     * @param request 微信登录请求（code）
     * @return 已绑定返回登录态（needsBind=false）；未绑定返回关联票据（needsBind=true）
     */
    public WechatLoginResponse wechatLogin(WechatLoginRequest request) {
        log.info("收到微信小程序登录请求");

        // 1. 通过code换取 openid/unionid
        WechatIdentity identity = getIdentityFromWechat(request.code());
        if (identity == null || identity.openid() == null || identity.openid().isBlank()) {
            throw new BusinessException(ErrorCode.WECHAT_AUTH_FAILED);
        }
        log.info("成功获取微信openid: {}", identity.openid());

        // 2. 查绑定关系：已绑定直接登录
        Optional<UserWechatBinding> binding = wechatBindingRepository
                .findByChannelAndOpenid(WechatChannel.MINIAPP, identity.openid());
        if (binding.isPresent()) {
            UserEntity user = userRepository.findById(binding.get().getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            LoginResponse login = buildLoginResponse(user);
            log.info("微信用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
            return new WechatLoginResponse(false, null, login);
        }

        // 3. 未绑定：openid 暂存 Redis（5min 一次性票据），不下发前端
        String ticket = bindTicketService.issue(identity.openid(), identity.unionid());
        log.info("微信未绑定账号，签发关联票据: openid={}", identity.openid());
        return new WechatLoginResponse(true, ticket, null);
    }

    /**
     * jscode2session 返回的微信身份
     */
    private record WechatIdentity(String openid, String unionid) {}

    /**
     * 通过微信code换取 openid/unionid
     *
     * @param code 微信授权码
     * @return 微信身份，失败返回 null
     */
    private WechatIdentity getIdentityFromWechat(String code) {
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

            String openid = jsonNode.has("openid") ? jsonNode.get("openid").asText() : null;
            // unionid 仅在绑定微信开放平台后返回，可能不存在
            String unionid = jsonNode.has("unionid") ? jsonNode.get("unionid").asText() : null;
            return new WechatIdentity(openid, unionid);

        } catch (Exception e) {
            log.error("调用微信API失败", e);
            return null;
        }
    }

    /**
     * 构建登录态（状态检查 + JWT 生成）
     */
    private LoginResponse buildLoginResponse(UserEntity user) {
        UserStatus status = user.getStatus();
        if (status != null && status != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "用户账号状态异常，请联系管理员");
        }
        return new LoginResponse(
                jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole().name()),
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }
}

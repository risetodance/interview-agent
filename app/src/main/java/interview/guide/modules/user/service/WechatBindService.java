package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.JwtTokenProvider;
import interview.guide.modules.user.dto.LoginResponse;
import interview.guide.modules.user.dto.WechatAuthDTOs.WechatBindByEmailRequest;
import interview.guide.modules.user.dto.WechatAuthDTOs.WechatBindByPasswordRequest;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.model.UserWechatBinding;
import interview.guide.modules.user.model.WechatChannel;
import interview.guide.modules.user.repository.UserRepository;
import interview.guide.modules.user.repository.UserWechatBindingRepository;
import interview.guide.modules.user.service.WechatBindTicketService.TicketPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 微信绑定服务
 * 微信未绑定账号时，用户凭票据 + Web 端凭证完成关联，绑定即登录。
 * 双通道：账号/邮箱+密码（覆盖无邮箱账号）、邮箱+验证码（复用 LOGIN 场景码基建）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatBindService {

    private final WechatBindTicketService bindTicketService;
    private final EmailCodeService emailCodeService;
    private final UserWechatBindingRepository wechatBindingRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 关联通道一：账号/邮箱 + 密码
     * 账号先按用户名精确匹配，再按规范化邮箱匹配（后端此前不存在"邮箱+密码"路径，此处补齐）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse bindByPassword(WechatBindByPasswordRequest request) {
        TicketPayload payload = bindTicketService.peek(request.ticket());

        UserEntity user = findUserByAccount(request.account());
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            // 统一报"账号或密码错误"，不区分账号是否存在（防账号枚举）；达阈值时 recordFailure 抛 TOO_MANY_FAILURES
            bindTicketService.recordFailure(request.ticket());
            throw new BusinessException(ErrorCode.INVALID_PASSWORD, "账号或密码错误");
        }
        return completeBind(user, payload, request.ticket());
    }

    /**
     * 关联通道二：邮箱 + 验证码
     * 复用 LOGIN 场景验证码（绑定即登录，语义同 EmailAuthService.loginByEmail 两步式）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse bindByEmailCode(WechatBindByEmailRequest request) {
        TicketPayload payload = bindTicketService.peek(request.ticket());

        final String email = EmailNormalizer.normalize(request.email());
        String verifiedCode;
        try {
            verifiedCode = emailCodeService.verifyCode(email, EmailCodeService.Scene.LOGIN, request.code());
        } catch (BusinessException e) {
            // 验证码错误/过期同样计入票据失败（防换着邮箱爆破票据）
            bindTicketService.recordFailure(request.ticket());
            throw e;
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    // 邮箱未注册：还原验证码（引导去注册后无需重新收码），模式同 loginByEmail
                    emailCodeService.restoreCode(email, EmailCodeService.Scene.LOGIN, verifiedCode);
                    return new BusinessException(ErrorCode.USER_NOT_FOUND, "该邮箱未注册，请先注册账号");
                });
        return completeBind(user, payload, request.ticket());
    }

    /**
     * 按账号定位用户：用户名精确匹配，未命中再按规范化邮箱匹配
     */
    private UserEntity findUserByAccount(String account) {
        String trimmed = account.trim();
        return userRepository.findByUsername(trimmed)
                .or(() -> userRepository.findByEmail(EmailNormalizer.normalize(trimmed)))
                .orElse(null);
    }

    /**
     * 公共收尾：状态检查 → 双向冲突检查 → 消费票据 → 落绑定 → 签发 JWT
     */
    private LoginResponse completeBind(UserEntity user, TicketPayload payload, String ticket) {
        UserStatus status = user.getStatus();
        if (status != null && status != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "用户账号状态异常，请联系管理员");
        }

        // 冲突一：该微信已绑在其他账号——报错拒绝，不做静默迁移（换绑走未来的解绑/换绑功能）
        wechatBindingRepository.findByChannelAndOpenid(WechatChannel.MINIAPP, payload.openid())
                .filter(binding -> !binding.getUserId().equals(user.getId()))
                .ifPresent(binding -> {
                    throw new BusinessException(ErrorCode.WECHAT_BIND_CONFLICT_WECHAT);
                });
        // 冲突二：该账号已绑其他微信
        wechatBindingRepository.findByUserIdAndChannel(user.getId(), WechatChannel.MINIAPP)
                .ifPresent(binding -> {
                    throw new BusinessException(ErrorCode.WECHAT_BIND_CONFLICT_ACCOUNT);
                });

        bindTicketService.invalidate(ticket);
        wechatBindingRepository.save(UserWechatBinding.builder()
                .userId(user.getId())
                .channel(WechatChannel.MINIAPP)
                .openid(payload.openid())
                .unionid(payload.unionid())
                .build());

        log.info("微信绑定成功: userId={}, username={}", user.getId(), user.getUsername());
        return new LoginResponse(
                jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole().name()),
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }
}

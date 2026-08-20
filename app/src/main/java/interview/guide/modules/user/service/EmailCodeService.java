package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

/**
 * 邮箱验证码服务
 * 负责验证码的生成、存储（Redis）、限发与校验生命周期管理。
 * <p>
 * 规则：
 * - 6 位数字，5 分钟有效（Redis TTL）
 * - 同一邮箱同一场景 60 秒内只能发一次（setIfAbsent 限发窗口）
 * - 同一验证码错误 5 次即作废，需重新获取（防爆破）
 * - 验证码按 scene 隔离，登录码不能用于重置密码等场景
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private final RedisService redisService;
    private final EmailSendService emailSendService;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 验证码有效期 */
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    /** 限发窗口：同一邮箱同一场景 60s 内仅可发一次 */
    private static final Duration SEND_INTERVAL = Duration.ofSeconds(60);
    /** 最大校验失败次数，超过后验证码作废 */
    private static final int MAX_VERIFY_FAILURES = 5;

    private static final String CODE_KEY_PREFIX = "email:code:";
    private static final String LIMIT_KEY_PREFIX = "email:code:limit:";
    private static final String FAIL_KEY_PREFIX = "email:code:fail:";

    /**
     * 验证码场景
     */
    public enum Scene {
        LOGIN("登录"),
        REGISTER("注册"),
        RESET_PASSWORD("重置密码"),
        CHANGE_PASSWORD("修改密码"),
        BIND_EMAIL("绑定邮箱");

        private final String description;

        Scene(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        /**
         * 从字符串解析场景（前端传 scene 字符串），非法值抛业务异常
         */
        public static Scene fromString(String value) {
            if (value == null || value.isBlank()) {
                throw new BusinessException(ErrorCode.EMAIL_SCENE_INVALID);
            }
            try {
                return Scene.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.EMAIL_SCENE_INVALID);
            }
        }
    }

    /**
     * 生成并发送验证码
     *
     * @param email 收件邮箱
     * @param scene 场景（登录/重置密码/修改密码/绑定邮箱）
     */
    public void sendCode(String email, Scene scene) {
        String limitKey = limitKey(email, scene);
        // setIfAbsent 成功 = 抢到 60s 发送窗口；失败 = 上一次发送还在窗口内
        if (!redisService.setIfAbsent(limitKey, "1", SEND_INTERVAL)) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_SEND_TOO_FREQUENT);
        }

        try {
            String code = generateCode();
            // F2/S9：先发信，成功后才写入新码并清失败计数——
            // SMTP 瞬时失败不会覆盖旧的有效验证码，旧码仍可用
            // ST-7：邮件正文的失效时间与 CODE_TTL 联动，避免改配置后文案与实际不符
            emailSendService.sendVerificationCode(email, code, scene.getDescription(), CODE_TTL.toMinutes());
            redisService.set(codeKey(email, scene), code, CODE_TTL);
            // 新验证码生效时清除旧验证码的失败计数
            redisService.delete(failKey(email, scene));
            log.info("邮箱验证码已生成并发送: email={}, scene={}", email, scene);
        } catch (Exception e) {
            // 发信失败：释放 60s 发送窗口，允许用户立即重试，再原样重抛原异常
            // 释放窗口的 delete 自身可能失败（如 Redis 瞬断）：只告警吞掉，不得掩盖原始发信异常
            try {
                redisService.delete(limitKey);
            } catch (Exception deleteEx) {
                log.warn("发信失败后释放限发窗口失败（不影响原样重抛发信异常）: limitKey={}, error={}",
                        limitKey, deleteEx.getMessage());
            }
            throw e;
        }
    }

    /**
     * 校验验证码：通过即消费（删除，一次性使用）
     *
     * @param email 邮箱
     * @param scene 场景
     * @param code  前端提交的验证码
     * @return 校验通过的码值（供调用方在需要时 restoreCode 恢复）
     * @throws BusinessException 过期/错误次数超限/不匹配
     */
    public String verifyCode(String email, Scene scene, String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        String codeKey = codeKey(email, scene);
        String stored = redisService.get(codeKey);
        if (stored == null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (!stored.equals(code.trim())) {
            String failKey = failKey(email, scene);
            long failures = redisService.increment(failKey);
            redisService.expire(failKey, CODE_TTL);
            if (failures >= MAX_VERIFY_FAILURES) {
                // 错误次数过多：作废当前验证码，强制重新获取
                redisService.delete(codeKey);
                log.warn("邮箱验证码错误次数超限已作废: email={}, scene={}", email, scene);
                throw new BusinessException(ErrorCode.EMAIL_CODE_TOO_MANY_FAILURES);
            }
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }
        // 验证通过：一次性消费，并清理失败计数
        redisService.delete(codeKey);
        redisService.delete(failKey(email, scene));
        return stored;
    }

    /**
     * 恢复刚消费的验证码（重设 TTL 并清失败计数）
     * 用于两步式登录/注册流程：第一步 login 校验通过但邮箱未注册时，
     * 把码还原给第二步 register 消费，用户无需重新收码。
     */
    public void restoreCode(String email, Scene scene, String code) {
        redisService.set(codeKey(email, scene), code, CODE_TTL);
        redisService.delete(failKey(email, scene));
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String codeKey(String email, Scene scene) {
        return CODE_KEY_PREFIX + scene.name() + ":" + email;
    }

    private String failKey(String email, Scene scene) {
        return FAIL_KEY_PREFIX + scene.name() + ":" + email;
    }

    private String limitKey(String email, Scene scene) {
        return LIMIT_KEY_PREFIX + scene.name() + ":" + email;
    }
}

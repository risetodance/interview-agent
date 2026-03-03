package interview.guide.modules.membership.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.membership.model.MembershipDTO;
import interview.guide.modules.user.model.MembershipType;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final UserRepository userRepository;

    /**
     * FREE用户默认额度
     */
    private static final int FREE_RESUME_QUOTA = 3;
    private static final int FREE_INTERVIEW_QUOTA = 5;
    private static final int FREE_AI_CALL_QUOTA = 50;

    /**
     * VIP用户额度（无限）
     */
    private static final int VIP_UNLIMITED = Integer.MAX_VALUE;

    /**
     * 获取用户会员信息
     *
     * @param userId 用户ID
     * @return 会员信息DTO
     */
    @Transactional(readOnly = true)
    public MembershipDTO getMembership(Long userId) {
        UserEntity user = getUserById(userId);

        return toMembershipDTO(user);
    }

    /**
     * 升级为VIP
     *
     * @param userId 用户ID
     * @return 会员信息DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public MembershipDTO upgradeToPremium(Long userId) {
        UserEntity user = getUserById(userId);

        // 如果已经是VIP，无需重复升级
        if (user.getMembership() == MembershipType.PREMIUM) {
            log.info("用户已是VIP会员: userId={}", userId);
            return toMembershipDTO(user);
        }

        // 升级为VIP（使用 immutable 模式，创建新对象）
        user.setMembership(MembershipType.PREMIUM);
        userRepository.save(user);

        log.info("用户升级为VIP成功: userId={}", userId);
        return toMembershipDTO(user);
    }

    /**
     * 检查并使用简历额度
     *
     * @param userId 用户ID
     * @return 是否成功使用额度
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean checkAndUseResumeQuota(Long userId) {
        UserEntity user = getUserById(userId);

        // VIP用户无限额度
        if (user.getMembership() == MembershipType.PREMIUM) {
            return true;
        }

        // FREE用户检查额度
        int usedQuota = user.getResumeQuotaUsed() != null ? user.getResumeQuotaUsed() : 0;
        if (usedQuota < FREE_RESUME_QUOTA) {
            // 扣减额度
            user.setResumeQuotaUsed(usedQuota + 1);
            userRepository.save(user);
            log.info("简历额度扣减成功: userId={}, usedQuota={}, maxQuota={}",
                    userId, usedQuota + 1, FREE_RESUME_QUOTA);
            return true;
        }

        log.warn("用户简历额度不足: userId={}, usedQuota={}, maxQuota={}",
                userId, usedQuota, FREE_RESUME_QUOTA);
        return false;
    }

    /**
     * 检查并使用面试额度
     *
     * @param userId 用户ID
     * @return 是否成功使用额度
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean checkAndUseInterviewQuota(Long userId) {
        UserEntity user = getUserById(userId);

        // VIP用户无限额度
        if (user.getMembership() == MembershipType.PREMIUM) {
            return true;
        }

        // FREE用户检查额度
        int usedQuota = user.getInterviewQuotaUsed() != null ? user.getInterviewQuotaUsed() : 0;
        if (usedQuota < FREE_INTERVIEW_QUOTA) {
            // 扣减额度
            user.setInterviewQuotaUsed(usedQuota + 1);
            userRepository.save(user);
            log.info("面试额度扣减成功: userId={}, usedQuota={}, maxQuota={}",
                    userId, usedQuota + 1, FREE_INTERVIEW_QUOTA);
            return true;
        }

        log.warn("用户面试额度不足: userId={}, usedQuota={}, maxQuota={}",
                userId, usedQuota, FREE_INTERVIEW_QUOTA);
        return false;
    }

    /**
     * 检查并使用AI调用额度
     *
     * @param userId 用户ID
     * @return 是否成功使用额度
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean checkAndUseAiCallQuota(Long userId) {
        UserEntity user = getUserById(userId);

        // VIP用户无限额度
        if (user.getMembership() == MembershipType.PREMIUM) {
            return true;
        }

        // FREE用户检查额度
        int usedQuota = user.getAiCallQuotaUsed() != null ? user.getAiCallQuotaUsed() : 0;
        if (usedQuota < FREE_AI_CALL_QUOTA) {
            // 扣减额度
            user.setAiCallQuotaUsed(usedQuota + 1);
            userRepository.save(user);
            log.info("AI调用额度扣减成功: userId={}, usedQuota={}, maxQuota={}",
                    userId, usedQuota + 1, FREE_AI_CALL_QUOTA);
            return true;
        }

        log.warn("用户AI调用额度不足: userId={}, usedQuota={}, maxQuota={}",
                userId, usedQuota, FREE_AI_CALL_QUOTA);
        return false;
    }

    /**
     * 根据用户ID获取用户实体
     */
    private UserEntity getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 将用户实体转换为会员信息DTO
     * 遵循 immutable 原则，返回新对象
     */
    private MembershipDTO toMembershipDTO(UserEntity user) {
        int resumeQuota;
        int interviewQuota;
        int aiCallQuota;

        if (user.getMembership() == MembershipType.PREMIUM) {
            resumeQuota = VIP_UNLIMITED;
            interviewQuota = VIP_UNLIMITED;
            aiCallQuota = VIP_UNLIMITED;
        } else {
            resumeQuota = FREE_RESUME_QUOTA;
            interviewQuota = FREE_INTERVIEW_QUOTA;
            aiCallQuota = FREE_AI_CALL_QUOTA;
        }

        return MembershipDTO.builder()
                .membership(user.getMembership())
                .points(user.getPoints())
                .resumeQuota(resumeQuota)
                .interviewQuota(interviewQuota)
                .aiCallQuota(aiCallQuota)
                .vipExpiryDate(null) // TODO: 如果有到期时间，需要从用户实体获取
                .build();
    }
}

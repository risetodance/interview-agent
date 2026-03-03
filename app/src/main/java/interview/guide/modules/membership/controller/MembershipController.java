package interview.guide.modules.membership.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.result.Result;
import interview.guide.modules.membership.model.MembershipDTO;
import interview.guide.modules.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员控制器
 * 处理会员信息、升级和额度相关请求
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/membership")
public class MembershipController {

    private final MembershipService membershipService;

    /**
     * 获取当前用户会员信息
     * GET /api/membership
     */
    @GetMapping
    public Result<MembershipDTO> getMembership(@CurrentUser Long userId) {
        log.debug("获取用户会员信息: userId={}", userId);
        MembershipDTO membership = membershipService.getMembership(userId);
        return Result.success(membership);
    }

    /**
     * 升级为VIP
     * POST /api/membership/upgrade
     */
    @PostMapping("/upgrade")
    public Result<MembershipDTO> upgradeToPremium(@CurrentUser Long userId) {
        log.info("用户升级为VIP: userId={}", userId);
        MembershipDTO membership = membershipService.upgradeToPremium(userId);
        return Result.success("升级成功", membership);
    }

    /**
     * 使用简历额度
     * POST /api/membership/resume/quota
     */
    @PostMapping("/resume/quota")
    public Result<Boolean> useResumeQuota(@CurrentUser Long userId) {
        log.debug("使用简历额度: userId={}", userId);
        boolean success = membershipService.checkAndUseResumeQuota(userId);
        if (success) {
            return Result.success("简历额度使用成功", true);
        } else {
            return Result.error("简历额度不足");
        }
    }

    /**
     * 使用面试额度
     * POST /api/membership/interview/quota
     */
    @PostMapping("/interview/quota")
    public Result<Boolean> useInterviewQuota(@CurrentUser Long userId) {
        log.debug("使用面试额度: userId={}", userId);
        boolean success = membershipService.checkAndUseInterviewQuota(userId);
        if (success) {
            return Result.success("面试额度使用成功", true);
        } else {
            return Result.error("面试额度不足");
        }
    }

    /**
     * 使用AI调用额度
     * POST /api/membership/ai/quota
     */
    @PostMapping("/ai/quota")
    public Result<Boolean> useAiCallQuota(@CurrentUser Long userId) {
        log.debug("使用AI调用额度: userId={}", userId);
        boolean success = membershipService.checkAndUseAiCallQuota(userId);
        if (success) {
            return Result.success("AI调用额度使用成功", true);
        } else {
            return Result.error("AI调用额度不足");
        }
    }
}

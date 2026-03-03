package interview.guide.modules.membership.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.result.Result;
import interview.guide.modules.membership.model.PointsRecordDTO;
import interview.guide.modules.membership.model.SignInStatusResponse;
import interview.guide.modules.membership.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 积分控制器
 * 处理积分查询、记录和签到相关请求
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointsController {

    private final PointsService pointsService;

    /**
     * 获取当前用户积分余额
     * GET /api/points
     */
    @GetMapping
    public Result<Integer> getPoints(@CurrentUser Long userId) {
        log.debug("获取用户积分余额: userId={}", userId);
        Integer points = pointsService.getPoints(userId);
        return Result.success(points);
    }

    /**
     * 获取积分记录列表
     * GET /api/points/history
     */
    @GetMapping("/history")
    public Result<List<PointsRecordDTO>> getPointsHistory(@CurrentUser Long userId) {
        log.debug("获取用户积分记录: userId={}", userId);
        List<PointsRecordDTO> history = pointsService.getPointsHistory(userId);
        return Result.success(history);
    }

    /**
     * 签到
     * POST /api/points/signin
     */
    @PostMapping("/signin")
    public Result<SignInStatusResponse> signIn(@CurrentUser Long userId) {
        log.info("用户签到: userId={}", userId);
        // 执行签到（添加积分）
        Integer pointsEarned = pointsService.signIn(userId);
        // 获取签到后的状态
        SignInStatusResponse status = pointsService.getSignInStatus(userId);
        status.setPointsCanEarn(pointsEarned);
        return Result.success("签到成功", status);
    }

    /**
     * 获取签到状态
     * GET /api/points/signin/status
     */
    @GetMapping("/signin/status")
    public Result<SignInStatusResponse> getSignInStatus(@CurrentUser Long userId) {
        log.debug("获取签到状态: userId={}", userId);
        SignInStatusResponse status = pointsService.getSignInStatus(userId);
        return Result.success(status);
    }
}

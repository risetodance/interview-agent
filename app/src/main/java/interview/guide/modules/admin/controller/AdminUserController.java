package interview.guide.modules.admin.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.result.Result;
import interview.guide.modules.admin.service.AdminUserService;
import interview.guide.modules.user.model.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 获取用户列表
     * GET /api/admin/users
     */
    @GetMapping
    public Result<Page<UserEntity>> getUserList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        log.info("获取用户列表: page={}, size={}, status={}, keyword={}", page, size, status, keyword);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserEntity> users;

        if (keyword != null && !keyword.isEmpty()) {
            users = adminUserService.searchUsers(keyword, pageable);
        } else if (status != null && !status.isEmpty()) {
            // 根据状态筛选
            try {
                var userStatus = interview.guide.modules.user.model.UserStatus.valueOf(status.toUpperCase());
                users = adminUserService.getUserListByStatus(userStatus, pageable);
            } catch (IllegalArgumentException e) {
                users = adminUserService.getUserList(pageable);
            }
        } else {
            users = adminUserService.getUserList(pageable);
        }

        return Result.success(users);
    }

    /**
     * 审核通过用户
     * PUT /api/admin/users/{id}/approve
     */
    @PutMapping("/{id}/approve")
    public Result<Void> approveUser(@PathVariable Long id) {
        log.info("审核通过用户: id={}", id);
        adminUserService.approveUser(id);
        return Result.success("审核通过", null);
    }

    /**
     * 审核拒绝用户
     * PUT /api/admin/users/{id}/reject
     */
    @PutMapping("/{id}/reject")
    public Result<Void> rejectUser(@PathVariable Long id) {
        log.info("审核拒绝用户: id={}", id);
        adminUserService.rejectUser(id);
        return Result.success("审核拒绝", null);
    }

    /**
     * 禁用用户
     * PUT /api/admin/users/{id}/disable
     */
    @PutMapping("/{id}/disable")
    public Result<Void> disableUser(@PathVariable Long id) {
        log.info("禁用用户: id={}", id);
        adminUserService.disableUser(id);
        return Result.success("用户已禁用", null);
    }

    /**
     * 启用用户
     * PUT /api/admin/users/{id}/enable
     */
    @PutMapping("/{id}/enable")
    public Result<Void> enableUser(@PathVariable Long id) {
        log.info("启用用户: id={}", id);
        adminUserService.enableUser(id);
        return Result.success("用户已启用", null);
    }

    /**
     * 获取待审核用户数量
     * GET /api/admin/users/pending/count
     */
    @GetMapping("/pending/count")
    public Result<Long> getPendingCount() {
        long count = adminUserService.getPendingUserCount();
        return Result.success(count);
    }
}

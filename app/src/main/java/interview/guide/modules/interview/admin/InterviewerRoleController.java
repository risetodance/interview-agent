package interview.guide.modules.interview.admin;

import interview.guide.common.result.Result;
import interview.guide.modules.interview.model.*;
import interview.guide.modules.interview.service.InterviewerRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 面试官角色管理控制器（后台管理）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/interviewer-roles")
@RequiredArgsConstructor
public class InterviewerRoleController {

    private final InterviewerRoleService roleService;

    /**
     * 获取所有角色列表
     * GET /api/admin/interviewer-roles
     */
    @GetMapping
    public Result<List<InterviewerRoleDTO>> getAllRoles() {
        log.info("获取所有面试官角色列表");
        List<InterviewerRoleDTO> roles = roleService.getAllRoles();
        return Result.success(roles);
    }

    /**
     * 获取单个角色详情
     * GET /api/admin/interviewer-roles/{id}
     */
    @GetMapping("/{id}")
    public Result<InterviewerRoleDTO> getRole(@PathVariable Long id) {
        log.info("获取面试官角色详情: id={}", id);
        InterviewerRoleDTO role = roleService.getRoleById(id);
        return Result.success(role);
    }

    /**
     * 创建角色
     * POST /api/admin/interviewer-roles
     */
    @PostMapping
    public Result<InterviewerRoleDTO> createRole(@Valid @RequestBody CreateInterviewerRoleRequest request) {
        log.info("创建面试官角色: roleName={}, roleCode={}", request.roleName(), request.roleCode());
        InterviewerRoleDTO role = roleService.createRole(request);
        return Result.success(role);
    }

    /**
     * 更新角色
     * PUT /api/admin/interviewer-roles/{id}
     */
    @PutMapping("/{id}")
    public Result<InterviewerRoleDTO> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody CreateInterviewerRoleRequest request) {
        log.info("更新面试官角色: id={}", id);
        InterviewerRoleDTO role = roleService.updateRole(id, request);
        return Result.success(role);
    }

    /**
     * 删除角色
     * DELETE /api/admin/interviewer-roles/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        log.info("删除面试官角色: id={}", id);
        roleService.deleteRole(id);
        return Result.success("删除成功", null);
    }

    /**
     * 更新角色权重
     * PUT /api/admin/interviewer-roles/{id}/weight
     */
    @PutMapping("/{id}/weight")
    public Result<InterviewerRoleDTO> updateWeight(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWeightRequest request) {
        log.info("更新角色权重: id={}, weight={}", id, request.weight());
        InterviewerRoleDTO role = roleService.updateWeight(id, request.weight());
        return Result.success(role);
    }
}

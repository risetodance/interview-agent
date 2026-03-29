package interview.guide.modules.interview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.*;
import interview.guide.modules.interview.repository.InterviewerRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 面试官角色管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewerRoleService {

    private final InterviewerRoleRepository roleRepository;

    /**
     * 获取所有角色列表
     */
    public List<InterviewerRoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有启用的角色
     */
    public List<InterviewerRoleDTO> getEnabledRoles() {
        return roleRepository.findByStatusTrueOrderBySortOrderAsc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取启用的默认模板角色
     */
    public List<InterviewerRoleDTO> getDefaultTemplateRoles() {
        return roleRepository.findByStatusTrueAndDefaultTemplateTrueOrderBySortOrderAsc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据ID获取角色
     */
    public InterviewerRoleDTO getRoleById(Long id) {
        return roleRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在"));
    }

    /**
     * 创建角色
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewerRoleDTO createRole(CreateInterviewerRoleRequest request) {
        // 检查角色编码是否已存在
        if (roleRepository.existsByRoleCode(request.roleCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "角色编码已存在");
        }

        InterviewerRoleEntity entity = new InterviewerRoleEntity();
        entity.setRoleName(request.roleName());
        entity.setRoleCode(request.roleCode());
        entity.setDescription(request.description());
        entity.setQuestionPrompt(request.questionPrompt());
        entity.setScoringPrompt(request.scoringPrompt());
        entity.setWeight(request.weight() != null ? request.weight() : 1.0);
        entity.setIcon(request.icon());
        entity.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        entity.setStatus(request.status() != null ? request.status() : true);
        entity.setDefaultTemplate(request.defaultTemplate() != null ? request.defaultTemplate() : false);

        InterviewerRoleEntity saved = roleRepository.save(entity);
        log.info("创建面试官角色: id={}, roleName={}, roleCode={}", saved.getId(), saved.getRoleName(), saved.getRoleCode());

        return toDTO(saved);
    }

    /**
     * 更新角色
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewerRoleDTO updateRole(Long id, CreateInterviewerRoleRequest request) {
        InterviewerRoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在"));

        // 检查角色编码是否与其他角色冲突
        if (roleRepository.existsByRoleCodeAndIdNot(request.roleCode(), id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "角色编码已存在");
        }

        entity.setRoleName(request.roleName());
        entity.setRoleCode(request.roleCode());
        entity.setDescription(request.description());
        entity.setQuestionPrompt(request.questionPrompt());
        entity.setScoringPrompt(request.scoringPrompt());
        if (request.weight() != null) {
            entity.setWeight(request.weight());
        }
        entity.setIcon(request.icon());
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.defaultTemplate() != null) {
            entity.setDefaultTemplate(request.defaultTemplate());
        }

        InterviewerRoleEntity saved = roleRepository.save(entity);
        log.info("更新面试官角色: id={}, roleName={}", saved.getId(), saved.getRoleName());

        return toDTO(saved);
    }

    /**
     * 更新角色权重
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewerRoleDTO updateWeight(Long id, Double weight) {
        InterviewerRoleEntity entity = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在"));

        entity.setWeight(weight);
        InterviewerRoleEntity saved = roleRepository.save(entity);
        log.info("更新角色权重: id={}, weight={}", id, weight);

        return toDTO(saved);
    }

    /**
     * 删除角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        roleRepository.deleteById(id);
        log.info("删除面试官角色: id={}", id);
    }

    private InterviewerRoleDTO toDTO(InterviewerRoleEntity entity) {
        return new InterviewerRoleDTO(
                entity.getId(),
                entity.getRoleName(),
                entity.getRoleCode(),
                entity.getDescription(),
                entity.getQuestionPrompt(),
                entity.getScoringPrompt(),
                entity.getWeight(),
                entity.getIcon(),
                entity.getSortOrder(),
                entity.getStatus(),
                entity.getDefaultTemplate(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
                entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null
        );
    }
}

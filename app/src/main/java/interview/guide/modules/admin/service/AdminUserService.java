package interview.guide.modules.admin.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.admin.model.AdminUserEntity;
import interview.guide.modules.admin.repository.AdminUserRepository;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final UserRepository userRepository;

    /**
     * 获取用户列表（分页）
     */
    public Page<UserEntity> getUserList(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /**
     * 根据状态获取用户列表
     */
    public Page<UserEntity> getUserListByStatus(UserStatus status, Pageable pageable) {
        return userRepository.findByStatus(status, pageable);
    }

    /**
     * 关键词搜索用户
     */
    public Page<UserEntity> searchUsers(String keyword, Pageable pageable) {
        return userRepository.findByUsernameContainingOrEmailContaining(keyword, keyword, pageable);
    }

    /**
     * 审核通过用户
     */
    @Transactional
    public void approveUser(Long userId) {
        UserEntity user = getUserById(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("审核通过用户: userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 审核拒绝用户
     */
    @Transactional
    public void rejectUser(Long userId) {
        UserEntity user = getUserById(userId);
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);
        log.info("审核拒绝用户: userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 禁用用户
     */
    @Transactional
    public void disableUser(Long userId) {
        UserEntity user = getUserById(userId);
        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);
        log.info("禁用用户: userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 启用用户
     */
    @Transactional
    public void enableUser(Long userId) {
        UserEntity user = getUserById(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("启用用户: userId={}, username={}", userId, user.getUsername());
    }

    /**
     * 获取待审核用户数量
     */
    public long getPendingUserCount() {
        return userRepository.countByStatus(UserStatus.INACTIVE);
    }

    /**
     * 根据ID获取用户
     */
    private UserEntity getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}

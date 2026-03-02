package interview.guide.modules.user.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.user.model.UserEntity;
import interview.guide.modules.user.model.UserProfileDTO;
import interview.guide.modules.user.model.UserStatus;
import interview.guide.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户查询服务
 */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户实体
     */
    public UserEntity getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    public UserEntity getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 根据用户名查询用户（可返回空值）
     *
     * @param username 用户名
     * @return 用户实体（可能为空）
     */
    public UserEntity getUserByUsernameOptional(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * 获取用户资料（不包含密码）
     *
     * @param userId 用户ID
     * @return 用户资料DTO
     */
    public UserProfileDTO getUserProfile(Long userId) {
        UserEntity user = getUserById(userId);
        validateUserStatus(user);
        return UserProfileDTO.fromEntity(user);
    }

    /**
     * 更新用户资料
     *
     * @param userId   用户ID
     * @param nickname 昵称（可为空）
     * @param avatar   头像URL（可为空）
     * @return 更新后的用户资料
     */
    @Transactional
    public UserProfileDTO updateProfile(Long userId, String nickname, String avatar) {
        UserEntity user = getUserById(userId);
        validateUserStatus(user);

        // 使用不可变原则，创建新实体或更新字段
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }

        UserEntity savedUser = userRepository.save(user);
        return UserProfileDTO.fromEntity(savedUser);
    }

    /**
     * 修改密码
     *
     * @param userId      用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return 更新后的用户资料
     */
    @Transactional
    public UserProfileDTO updatePassword(Long userId, String oldPassword, String newPassword) {
        UserEntity user = getUserById(userId);
        validateUserStatus(user);

        // 验证原密码
        if (user.getPassword() != null && !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }

        // 使用 BCrypt 加密新密码
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        UserEntity savedUser = userRepository.save(user);
        return UserProfileDTO.fromEntity(savedUser);
    }

    /**
     * 验证用户状态
     */
    private void validateUserStatus(UserEntity user) {
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BusinessException(ErrorCode.USER_INACTIVE);
        }
    }

    /**
     * 检查用户名是否存在
     *
     * @param username 用户名
     * @return 是否存在
     */
    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    /**
     * 检查邮箱是否存在
     *
     * @param email 邮箱
     * @return 是否存在
     */
    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}

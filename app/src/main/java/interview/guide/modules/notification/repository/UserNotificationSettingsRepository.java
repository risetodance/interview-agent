package interview.guide.modules.notification.repository;

import interview.guide.modules.notification.model.UserNotificationSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户通知设置 Repository
 */
@Repository
public interface UserNotificationSettingsRepository extends JpaRepository<UserNotificationSettingsEntity, Long> {

    /**
     * 根据用户ID查询通知设置
     */
    Optional<UserNotificationSettingsEntity> findByUserId(Long userId);
}

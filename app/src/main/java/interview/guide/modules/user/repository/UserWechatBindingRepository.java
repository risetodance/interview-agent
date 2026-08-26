package interview.guide.modules.user.repository;

import interview.guide.modules.user.model.UserWechatBinding;
import interview.guide.modules.user.model.WechatChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户微信绑定 Repository
 */
@Repository
public interface UserWechatBindingRepository extends JpaRepository<UserWechatBinding, Long> {

    /**
     * 按渠道 + openid 查绑定（微信侧登录定位账号）
     *
     * @param channel 微信应用渠道
     * @param openid  微信openid
     * @return 绑定记录
     */
    Optional<UserWechatBinding> findByChannelAndOpenid(WechatChannel channel, String openid);

    /**
     * 按用户 + 渠道查绑定（账号侧检查是否已绑微信）
     *
     * @param userId  用户ID
     * @param channel 微信应用渠道
     * @return 绑定记录
     */
    Optional<UserWechatBinding> findByUserIdAndChannel(Long userId, WechatChannel channel);
}

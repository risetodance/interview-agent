package interview.guide.modules.notification.service;

import interview.guide.modules.notification.dto.WechatSendRequest;
import interview.guide.modules.notification.dto.WechatSubscribeRequest;
import interview.guide.modules.notification.enums.NotificationChannel;
import interview.guide.modules.notification.model.NotificationEntity;
import interview.guide.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信消息服务
 * 负责发送微信订阅消息和处理通知到订阅消息的转换
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMessageService {

    private final NotificationRepository notificationRepository;
    private final RestTemplate restTemplate;

    /**
     * 微信小程序AppID
     */
    @Value("${spring.wechat.miniapp.appid:}")
    private String miniAppAppId;

    /**
     * 微信小程序AppSecret
     */
    @Value("${spring.wechat.miniapp.secret:}")
    private String miniAppSecret;

    /**
     * 发送订阅消息的API地址
     */
    private static final String WECHAT_SUBSCRIBE_SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";

    /**
     * 获取access_token的API地址
     */
    private static final String WECHAT_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";

    /**
     * 缓存access_token（简单内存缓存，生产环境建议使用Redis）
     */
    private static final Map<String, TokenInfo> TOKEN_CACHE = new ConcurrentHashMap<>();

    /**
     * 发送微信订阅消息
     *
     * @param request 订阅消息请求
     * @return 是否发送成功
     */
    public boolean sendSubscribeMessage(WechatSendRequest request) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.error("获取微信access_token失败");
                return false;
            }

            String url = WECHAT_SUBSCRIBE_SEND_URL + "?access_token=" + accessToken;

            // 构建请求体
            Map<String, Object> body = Map.of(
                    "touser", request.getOpenId(),
                    "template_id", request.getTemplateId(),
                    "data", parseTemplateData(request.getData()),
                    "page", request.getPage() != null ? request.getPage() : ""
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer errCode = (Integer) response.getBody().get("errcode");
                String errMsg = (String) response.getBody().get("errmsg");

                if (errCode != null && errCode == 0) {
                    log.info("微信订阅消息发送成功: openId={}, templateId={}", request.getOpenId(), request.getTemplateId());
                    return true;
                } else {
                    log.error("微信订阅消息发送失败: errcode={}, errmsg={}", errCode, errMsg);
                    return false;
                }
            }

            return false;
        } catch (Exception e) {
            log.error("发送微信订阅消息异常", e);
            return false;
        }
    }

    /**
     * 发送通知到微信订阅消息
     * 将系统通知转换为微信订阅消息发送
     *
     * @param userId    用户ID
     * @param templateId 模板ID
     * @param openId    用户openId
     * @param title     通知标题
     * @param content  通知内容
     * @param page     跳转页面（可选）
     * @return 是否发送成功
     */
    public boolean sendNotificationToWechat(Long userId, String templateId, String openId,
                                            String title, String content, String page) {
        // 构建模板数据
        String templateData = buildTemplateData(title, content);

        WechatSendRequest request = WechatSendRequest.builder()
                .openId(openId)
                .templateId(templateId)
                .data(templateData)
                .page(page)
                .build();

        boolean success = sendSubscribeMessage(request);

        // 记录通知发送日志
        if (success) {
            log.info("通知转微信订阅消息发送成功: userId={}, openId={}", userId, openId);
        } else {
            log.warn("通知转微信订阅消息发送失败: userId={}, openId={}", userId, openId);
        }

        return success;
    }

    /**
     * 转换站内通知到微信订阅消息
     * 查找用户的未发送微信通知并发送
     *
     * @param userId 用户ID
     * @param templateId 模板ID
     * @param openId 用户openId
     * @return 成功发送的数量
     */
    public int convertNotificationToWechat(Long userId, String templateId, String openId) {
        // 查询该用户的站内通知
        var notifications = notificationRepository.findByUserIdAndChannelAndIsSentFalse(
                userId, NotificationChannel.IN_APP);

        int sentCount = 0;
        for (NotificationEntity notification : notifications) {
            boolean success = sendNotificationToWechat(
                    userId,
                    templateId,
                    openId,
                    notification.getTitle(),
                    notification.getContent(),
                    null
            );

            if (success) {
                // 标记为已发送
                notification.setIsSent(true);
                notificationRepository.save(notification);
                sentCount++;
            }
        }

        log.info("转换站内通知到微信订阅消息完成: userId={}, 成功发送={}", userId, sentCount);
        return sentCount;
    }

    /**
     * 获取微信access_token
     *
     * @return access_token，获取失败返回null
     */
    private String getAccessToken() {
        // 检查缓存
        TokenInfo cachedToken = TOKEN_CACHE.get("miniapp");
        if (cachedToken != null && !cachedToken.isExpired()) {
            return cachedToken.getToken();
        }

        try {
            String url = WECHAT_ACCESS_TOKEN_URL + "?appid=" + miniAppAppId
                    + "&secret=" + miniAppSecret
                    + "&grant_type=client_credential";

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String accessToken = (String) response.getBody().get("access_token");
                Integer expiresIn = (Integer) response.getBody().get("expires_in");

                if (accessToken != null) {
                    // 缓存token
                    TOKEN_CACHE.put("miniapp", new TokenInfo(accessToken,
                            expiresIn != null ? expiresIn : 7200));
                    return accessToken;
                } else {
                    Integer errCode = (Integer) response.getBody().get("errcode");
                    String errMsg = (String) response.getBody().get("errmsg");
                    log.error("获取微信access_token失败: errcode={}, errmsg={}", errCode, errMsg);
                }
            }

            return null;
        } catch (Exception e) {
            log.error("获取微信access_token异常", e);
            return null;
        }
    }

    /**
     * 解析模板数据（将JSON字符串转换为Map）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseTemplateData(String dataJson) {
        // 简单实现，实际项目中可以使用Jackson解析
        // 这里假设data已经是正确的Map格式
        try {
            if (dataJson.startsWith("{")) {
                return Map.of("first", Map.of("value", dataJson, "color", "#173177"));
            }
            return Map.of("first", Map.of("value", dataJson, "color", "#173177"));
        } catch (Exception e) {
            log.error("解析模板数据失败", e);
            return Map.of();
        }
    }

    /**
     * 构建模板数据
     */
    private String buildTemplateData(String title, String content) {
        // 构建微信订阅消息模板数据格式
        // 实际模板格式需要根据具体的模板ID定义
        return String.format(
                "{\"first\": {\"value\": \"%s\", \"color\": \"#173177\"}, \"keyword1\": {\"value\": \"%s\", \"color\": \"#666666\"}, \"keyword2\": {\"value\": \"%s\", \"color\": \"#666666\"}, \"remark\": {\"value\": \"点击查看详情\", \"color\": \"#173177\"}}",
                title, "系统通知", content
        );
    }

    /**
     * Token信息内部类
     */
    private static class TokenInfo {
        private final String token;
        private final long expiresTime;

        public TokenInfo(String token, int expiresIn) {
            this.token = token;
            // 提前5分钟过期
            this.expiresTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
        }

        public String getToken() {
            return token;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresTime;
        }
    }
}

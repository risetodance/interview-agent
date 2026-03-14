package interview.guide.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信订阅消息请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WechatSubscribeRequest {

    /**
     * 模板ID
     */
    private String templateId;

    /**
     * 用户openId（小程序或公众号）
     */
    private String openId;
}

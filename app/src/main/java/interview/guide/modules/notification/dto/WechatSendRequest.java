package interview.guide.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信发送订阅消息请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WechatSendRequest {

    /**
     * 接收者openId
     */
    private String openId;

    /**
     * 模板ID
     */
    private String templateId;

    /**
     * 模板内容，JSON格式
     * 例如: {"first": {"value": "xxx", "color": "#173177"}, "keyword1": {...}, ...}
     */
    private String data;

    /**
     * 点击模板卡片后的跳转页面
     */
    private String page;
}

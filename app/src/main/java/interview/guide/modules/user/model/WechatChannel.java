package interview.guide.modules.user.model;

/**
 * 微信应用渠道
 * openid 按微信应用隔离（同一微信用户在不同应用下 openid 不同），
 * 绑定关系必须带渠道维度，避免不同应用的 openid 混淆
 */
public enum WechatChannel {
    /**
     * 微信小程序
     */
    MINIAPP,

    /**
     * 微信网页扫码登录（预留渠道，本次改造不接入，绑定表已留位）
     */
    WEB_SCAN
}

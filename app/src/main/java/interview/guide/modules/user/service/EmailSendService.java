package interview.guide.modules.user.service;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import com.tencentcloudapi.ses.v20201002.models.Template;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 邮件发送服务
 * 腾讯云 SES SendEmail API 模板发信（腾讯云已对个人用户停用 SMTP，且 Simple 直接正文方式已废弃，
 * 未申请特殊配置的账号调用 Simple 会返回 FailedOperation.WithOutPermission，仅支持模板发送，
 * 见官方文档 https://cloud.tencent.com/document/product/1288/51034）
 * 模板需在 SES 控制台"发信模板"中创建并审核通过（用户操作），模板 ID 配置到 .env 的 SES_TEMPLATE_* 变量
 * 配置见 application.yml 的 ses 段与 .env 的 SES_* 变量
 */
@Slf4j
@Service
public class EmailSendService {

    /**
     * 发件人展示名（如"AI 面试指南"），拼入发件人别名；实际发信地址为 ses.from-email。
     * 邮件主题由 API 的 Subject 参数传入（必选，模板只决定正文），展示名不参与主题
     */
    @Value("${ses.from-name:AI 面试指南}")
    private String fromName;

    /**
     * 腾讯云访问密钥 secretId（建议子账号密钥且只授予 SES 权限）
     */
    @Value("${ses.secret-id:}")
    private String secretId;

    /**
     * 腾讯云访问密钥 secretKey（建议子账号密钥且只授予 SES 权限）
     */
    @Value("${ses.secret-key:}")
    private String secretKey;

    /**
     * 接口地域（SendEmail 仅支持 ap-guangzhou / ap-hongkong）
     */
    @Value("${ses.region:ap-guangzhou}")
    private String region;

    /**
     * SES 控制台配置的发信地址（如 noreply@yourdomain.com）
     */
    @Value("${ses.from-email:}")
    private String fromEmail;

    /**
     * 验证码邮件模板 ID（SES 控制台"发信模板"创建并审核通过后取得；缺省 0 表示未配置）
     */
    @Value("${ses.template-code-id:0}")
    private Long templateCodeId;

    /**
     * 换绑通知邮件模板 ID（SES 控制台"发信模板"创建并审核通过后取得；缺省 0 表示未配置）
     */
    @Value("${ses.template-notify-id:0}")
    private Long templateNotifyId;

    /**
     * 可复用的 SES API 客户端（凭据齐全时在 @PostConstruct 初始化一次，SDK 客户端线程安全）
     */
    private volatile SesClient sesClient;

    /**
     * 启动时初始化 SES 客户端：
     * 凭据任一缺失（secretId / secretKey / fromEmail）则跳过初始化并打防呆日志（仅说明缺哪项，不打凭据值），
     * 发送时再抛 EMAIL_NOT_CONFIGURED，保持"未配置即明确报错"的错误行为不变
     */
    @PostConstruct
    void initSesClient() {
        String missing = missingConfigItem();
        if (missing != null) {
            log.warn("腾讯云 SES 凭据未配置（缺 {}），邮件功能不可用，请补齐 .env 中的 SES_* 配置", missing);
            return;
        }
        Credential credential = new Credential(secretId, secretKey);
        // 显式指定 API 域名，region 由 SES 控制台所在地域决定
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("ses.tencentcloudapi.com");
        // SDK 默认 readTimeout/writeTimeout=0（okhttp 无限等待），SES 网络黑洞会让调用线程无限挂起，
        // 显式设置连接/读/写超时（单位：秒，SDK 内部以 TimeUnit.SECONDS 传给 okhttp）防止挂起，
        // 与仓库其他 HTTP 客户端显式超时的做法保持一致
        httpProfile.setConnTimeout(10);
        httpProfile.setReadTimeout(10);
        httpProfile.setWriteTimeout(10);
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        this.sesClient = new SesClient(credential, region, clientProfile);
        log.info("腾讯云 SES 客户端初始化成功: region={}, fromEmail={}", region, fromEmail);
    }

    /**
     * 发送邮箱验证码邮件（模板方式，使用 ses.template-code-id 对应模板）
     * 模板需在 SES 控制台创建并审核通过，模板正文占位变量为：{{code}}（验证码）、
     * {{scene}}（场景描述）、{{minutes}}（有效分钟数）；
     * 模板正文示例：您正在进行{{scene}}操作，验证码为 {{code}}，{{minutes}} 分钟内有效。如非本人操作，请忽略本邮件。
     *
     * @param to         收件人邮箱
     * @param code       验证码（6 位数字）
     * @param sceneDesc  场景描述（如"登录"、"重置密码"，来自服务端枚举），填充模板 {{scene}} 变量
     * @param ttlMinutes 验证码有效分钟数（由调用方的 CODE_TTL 换算传入，保证正文与实际有效期一致）
     */
    public void sendVerificationCode(String to, String code, String sceneDesc, long ttlMinutes) {
        Long templateId = requireTemplateId(templateCodeId, "SES_TEMPLATE_CODE_ID");
        // TemplateData 必须是合法 JSON 字符串：code 为 6 位数字、sceneDesc 来自服务端枚举、ttlMinutes 为数字，
        // 三个变量均为受控值（不含引号与反斜杠），无 JSON 注入面，直接拼接即可
        String templateData = String.format("{\"code\":\"%s\",\"scene\":\"%s\",\"minutes\":%d}",
                code, sceneDesc, ttlMinutes);
        // Subject 为 SendEmail API 必选参数（模板只决定正文）：直接拼场景描述而非 {{scene}} 模板变量，更直观；
        // 主题含场景描述可提高用户辨识度，降低钓鱼邮件混淆
        sendTemplateMail(to, templateId, templateData, "您的" + sceneDesc + "验证码", "验证码邮件");
    }

    /**
     * 发送"绑定邮箱已变更"通知邮件（模板方式，使用 ses.template-notify-id 对应模板）
     * 换绑成功后发往旧邮箱，让原邮箱主人有机会发现异常换绑
     * 模板需在 SES 控制台创建并审核通过，模板正文占位变量为：{{newEmail}}（变更后的绑定邮箱）；
     * 模板正文示例：您的账号绑定邮箱已变更为 {{newEmail}}。如非本人操作，请立即登录修改密码。
     *
     * @param oldEmail 变更前的绑定邮箱（收件人）
     * @param newEmail 变更后的绑定邮箱（仅在正文中展示，用户输入值，需 JSON 转义）
     */
    public void sendEmailChangedNotification(String oldEmail, String newEmail) {
        Long templateId = requireTemplateId(templateNotifyId, "SES_TEMPLATE_NOTIFY_ID");
        // newEmail 为用户输入，含引号/反斜杠会破坏 JSON 结构（SES 报 FailedOperation.WrongContentJson），需转义
        String templateData = String.format("{\"newEmail\":\"%s\"}", escapeJson(newEmail));
        // Subject 为 SendEmail API 必选参数（模板只决定正文），固定文案即可
        sendTemplateMail(oldEmail, templateId, templateData, "您的账号邮箱已变更", "邮箱变更通知");
    }

    /**
     * 调用腾讯云 SES SendEmail API 发送模板邮件的统一入口
     * 发件人别名沿用官方"别名+一个空格+<邮箱地址>"格式（别名不能带冒号）；
     * 邮件主题由 API 的 Subject 参数传入（官方文档标注为必选，缺省会请求被拒），模板只决定正文
     *
     * @param to           收件人
     * @param templateId   SES 控制台审核通过的模板 ID
     * @param templateData 模板变量 JSON 字符串（须为合法 JSON，否则报 FailedOperation.WrongContentJson）
     * @param subject      邮件主题（必选参数，模板方式下也支持 {{变量}} 填充）
     * @param mailType     邮件类型描述（用于日志）
     */
    private void sendTemplateMail(String to, Long templateId, String templateData, String subject, String mailType) {
        SesClient client = this.sesClient;
        if (client == null) {
            // SES_SECRET_ID / SES_SECRET_KEY / SES_FROM_EMAIL 任一未配置时无法调用 API，提前给出明确错误
            log.error("邮件服务未配置（缺 {}），无法发送{}: to={}", missingConfigItem(), mailType, to);
            throw new BusinessException(ErrorCode.EMAIL_NOT_CONFIGURED);
        }

        try {
            SendEmailRequest request = new SendEmailRequest();
            request.setFromEmailAddress(buildFromAddress());
            request.setDestination(new String[]{to});
            // Subject 为必选参数：模板只决定正文，主题必须由 API 传入
            request.setSubject(subject);
            Template template = new Template();
            template.setTemplateID(templateId);
            template.setTemplateData(templateData);
            request.setTemplate(template);
            // 触发类邮件（验证码等即时发送类），走即时通道提升送达时效
            request.setTriggerType(1L);

            SendEmailResponse response = client.SendEmail(request);
            log.info("{}已发送: to={}, messageId={}", mailType, to, response.getMessageId());
        } catch (TencentCloudSDKException e) {
            // 仅记录 SDK 错误信息（e.getMessage 含服务端错误码与原因），不打印任何凭据
            log.error("{}发送失败: to={}, error={}", mailType, to, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        } catch (RuntimeException e) {
            // 兜底捕获 SDK 底层 okhttp 等抛出的极端 unchecked 异常，统一转为 EMAIL_SEND_FAILED，
            // 避免穿透到全局异常处理变成未预期的 500
            log.error("{}发送异常: to={}, error={}", mailType, to, e.getMessage(), e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    /**
     * 校验模板 ID 已配置（SES 控制台创建模板并审核通过后填入，缺省 0 表示未配置），
     * 未配置时抛 EMAIL_NOT_CONFIGURED；两个发送方法各自检查各自使用的模板 ID，
     * 允许只配置其中一个模板（如只配验证码模板时验证码邮件仍可发送）
     *
     * @param templateId     配置的模板 ID
     * @param configItemName 对应的配置项名（用于防呆日志，不涉及凭据）
     * @return 可用的模板 ID
     */
    private Long requireTemplateId(Long templateId, String configItemName) {
        if (templateId == null || templateId <= 0) {
            log.error("邮件模板未配置（{}），请先在 SES 控制台创建模板并通过审核后填入 .env", configItemName);
            throw new BusinessException(ErrorCode.EMAIL_NOT_CONFIGURED);
        }
        return templateId;
    }

    /**
     * 极简 JSON 字符串值转义：处理反斜杠、双引号与换行符（newEmail 已通过邮箱格式校验，
     * 理论上不会出现换行，仅 quoted local part 等理论路径可能混入，做 defense-in-depth），
     * 防止用户输入破坏 TemplateData 的 JSON 结构；注意必须先转义反斜杠再转义引号，避免二次转义
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * 构建发件人地址：官方格式为"别名+一个空格+<邮箱地址>"（别名不能带冒号）；
     * 展示名含冒号（官方不允许）时退化为纯地址，避免发信被拒
     */
    private String buildFromAddress() {
        if (StringUtils.hasText(fromName) && !fromName.contains(":")) {
            return fromName + " <" + fromEmail + ">";
        }
        return fromEmail;
    }

    /**
     * 返回第一个缺失的配置项名（用于防呆日志），全部齐全返回 null
     */
    private String missingConfigItem() {
        if (!StringUtils.hasText(secretId)) {
            return "SES_SECRET_ID";
        }
        if (!StringUtils.hasText(secretKey)) {
            return "SES_SECRET_KEY";
        }
        if (!StringUtils.hasText(fromEmail)) {
            return "SES_FROM_EMAIL";
        }
        return null;
    }
}

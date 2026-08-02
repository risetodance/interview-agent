package interview.guide.modules.aimodel.controller;

import interview.guide.common.annotation.CurrentUser;
import interview.guide.common.result.Result;
import interview.guide.modules.aimodel.enums.AiModelConfigType;
import interview.guide.modules.aimodel.model.AiModelConfigDTO;
import interview.guide.modules.aimodel.model.AiModelConfigListResponse;
import interview.guide.modules.aimodel.model.AiModelConfigRequest;
import interview.guide.modules.aimodel.model.ProbeRequest;
import interview.guide.modules.aimodel.model.ProbeResult;
import interview.guide.modules.aimodel.model.TestRequest;
import interview.guide.modules.aimodel.model.TestResult;
import interview.guide.modules.aimodel.service.AiModelConfigService;
import interview.guide.modules.aimodel.service.AiModelProbeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模型配置控制器
 * <p>路径前缀 /api/admin/ai-models，默认走 ADMIN 权限，无需加白名单。
 * <p>角色指派模型：列表返回「凭证 + 当前角色指派」；角色指派 / 禁用走 /role/{role}/... 子路径。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai-models")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final AiModelConfigService aiModelConfigService;
    private final AiModelProbeService aiModelProbeService;

    /** 列表：全部凭证 + 当前角色指派映射（{CHAT: id|null, SMALL_CHAT: id|null}） */
    @GetMapping
    public Result<AiModelConfigListResponse> list() {
        return Result.success(aiModelConfigService.list());
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<AiModelConfigDTO> get(@PathVariable Long id) {
        return Result.success(aiModelConfigService.get(id));
    }

    /** 新建（纯凭证，不绑角色） */
    @PostMapping
    public Result<AiModelConfigDTO> create(@Valid @RequestBody AiModelConfigRequest request,
                                           @CurrentUser Long operatorId,
                                           @CurrentUser String operatorUsername) {
        log.info("新建 AI 模型配置: displayName={}", request.getDisplayName());
        return Result.success(aiModelConfigService.create(request, operatorId, operatorUsername));
    }

    /** 更新（api_key 为空 / 脱敏占位则不修改；被引用时触发热生效） */
    @PutMapping("/{id}")
    public Result<AiModelConfigDTO> update(@PathVariable Long id,
                                           @Valid @RequestBody AiModelConfigRequest request,
                                           @CurrentUser Long operatorId,
                                           @CurrentUser String operatorUsername) {
        log.info("更新 AI 模型配置: id={}", id);
        return Result.success(aiModelConfigService.update(id, request, operatorId, operatorUsername));
    }

    /** 指派角色（CHAT 不允许置空；SMALL_CHAT 置空 = 禁用退化主模型，请走 disable 接口） */
    @PutMapping("/role/{role}/assign/{configId}")
    public Result<Void> assignRole(@PathVariable AiModelConfigType role,
                                   @PathVariable Long configId,
                                   @CurrentUser Long operatorId,
                                   @CurrentUser String operatorUsername) {
        log.info("指派 AI 模型角色: role={}, configId={}", role, configId);
        aiModelConfigService.assignRole(role, configId, operatorId, operatorUsername);
        return Result.success("指派成功", null);
    }

    /** 禁用角色（仅 SMALL_CHAT；CHAT 会被拒绝并返回 AI_MODEL_ROLE_CHAT_REQUIRED） */
    @PutMapping("/role/{role}/disable")
    public Result<Void> disableRole(@PathVariable AiModelConfigType role,
                                    @CurrentUser Long operatorId,
                                    @CurrentUser String operatorUsername) {
        log.info("禁用 AI 模型角色: role={}", role);
        aiModelConfigService.disableRole(role, operatorId, operatorUsername);
        return Result.success("已禁用（小模型将退化使用主模型）", null);
    }

    /** 删除（被角色引用时拒绝，需先取消指派） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @CurrentUser Long operatorId,
                               @CurrentUser String operatorUsername) {
        log.info("删除 AI 模型配置: id={}", id);
        aiModelConfigService.delete(id, operatorId, operatorUsername);
        return Result.success("删除成功", null);
    }

    /** 拉取可用模型列表 */
    @PostMapping("/probe")
    public Result<ProbeResult> probe(@RequestBody ProbeRequest body) {
        return Result.success(aiModelProbeService.probe(body));
    }

    /** 测试连接（支持未保存前测试） */
    @PostMapping("/test")
    public Result<TestResult> test(@RequestBody TestRequest body) {
        return Result.success(aiModelProbeService.test(body.getId(), body.getBaseUrl(), body.getApiKey(), body.getModelName()));
    }
}

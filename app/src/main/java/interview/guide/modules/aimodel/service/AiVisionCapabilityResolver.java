package interview.guide.modules.aimodel.service;

import interview.guide.modules.aimodel.enums.AiModelConfigType;
import interview.guide.modules.aimodel.model.AiModelConfigEntity;
import interview.guide.modules.aimodel.repository.AiModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 视觉能力解析器
 * <p>按 CHAT → SMALL_CHAT 槽位顺序解析当前可用的视觉（图片输入）模型候选：
 * <ul>
 *   <li>槽位已指派且该凭证 supports_vision=true → 入候选（主模型优先，主不支持退化小模型）</li>
 *   <li>同一凭证被两个槽位引用 → 去重，只按第一个槽位计一次</li>
 *   <li>都不满足 → 空列表，简历解析维持纯文本链路（现状）</li>
 * </ul>
 * 注意：supports_vision 只是能力声明（管理员自行保证模型真实支持图像输入），
 * 调用失败时的退化由调用方按候选顺序依次重试实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVisionCapabilityResolver {

    private final AiModelConfigRepository aiModelConfigRepository;
    private final AiModelRegistry aiModelRegistry;

    /** 视觉模型候选：role = 候选来自的槽位（调用失败可按序退到下一候选） */
    public record VisionCandidate(AiModelConfigType role, AiModelConfigEntity config) {}

    /**
     * 解析当前可用的视觉模型候选，按 CHAT → SMALL_CHAT 有序返回。
     */
    public List<VisionCandidate> resolveVisionCandidates() {
        List<VisionCandidate> candidates = new ArrayList<>();
        Set<Long> seenConfigIds = new HashSet<>();
        for (AiModelConfigType role : List.of(AiModelConfigType.CHAT, AiModelConfigType.SMALL_CHAT)) {
            Long configId = aiModelRegistry.getActiveConfigId(role);
            if (configId == null || !seenConfigIds.add(configId)) {
                continue;
            }
            aiModelConfigRepository.findById(configId)
                    .filter(config -> Boolean.TRUE.equals(config.getSupportsVision()))
                    .ifPresent(config -> candidates.add(new VisionCandidate(role, config)));
        }
        if (log.isDebugEnabled()) {
            log.debug("视觉模型候选: {}", candidates.stream()
                    .map(c -> c.role() + "#" + c.config().getId() + "(" + c.config().getModelName() + ")")
                    .toList());
        }
        return candidates;
    }

    /**
     * 是否视觉优先：以第一个视觉候选（主模型优先）的 vision_priority 配置为准。
     * true = 简历 PDF 一律先视觉识别（失败回退文本）；false = 文本解析失败/过少才兜底视觉。
     */
    public static boolean isVisionPriority(List<VisionCandidate> candidates) {
        return !candidates.isEmpty() && Boolean.TRUE.equals(candidates.get(0).config().getVisionPriority());
    }
}

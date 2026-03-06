package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.mapper.KnowledgeBaseMapper;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.membership.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 知识库共享服务
 * 负责知识库的公开/私有设置、公开知识库搜索、引用等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseShareService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final PointsService pointsService;

    /**
     * 设置知识库公开/私有
     *
     * @param userId 用户ID
     * @param id     知识库ID
     * @param isPublic 是否公开
     * @return 更新后的知识库
     */
    @Transactional
    public KnowledgeBaseListItemDTO setVisibility(Long userId, Long id, boolean isPublic) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在或无权操作"));

        // 获取设置前的公开状态
        boolean wasPublic = Boolean.TRUE.equals(entity.getIsPublic());

        // 更新公开状态
        entity.setIsPublic(isPublic);
        knowledgeBaseRepository.save(entity);

        log.info("知识库公开/私有设置: id={}, userId={}, isPublic={}, wasPublic={}",
                id, userId, isPublic, wasPublic);

        // 如果是从私有改为公开，奖励分享积分
        if (isPublic && !wasPublic) {
            try {
                pointsService.shareKnowledgeBase(userId, id);
            } catch (Exception e) {
                // 积分奖励失败不影响主流程，仅记录日志
                log.warn("知识库分享积分奖励失败: id={}, userId={}, error={}", id, userId, e.getMessage());
            }
        }

        return knowledgeBaseMapper.toListItemDTO(entity);
    }

    /**
     * 获取所有公开知识库列表
     *
     * @param sortBy 排序字段: usage - 按引用次数, time - 按上传时间
     * @return 公开知识库列表
     */
    @Transactional(readOnly = true)
    public List<KnowledgeBaseListItemDTO> listPublicKnowledgeBases(String sortBy) {
        List<KnowledgeBaseEntity> entities;

        if ("time".equalsIgnoreCase(sortBy)) {
            entities = knowledgeBaseRepository.findByIsPublicTrueOrderByUploadedAtDesc();
        } else {
            // 默认按引用次数排序
            entities = knowledgeBaseRepository.findByIsPublicTrueOrderByUsageCountDesc();
        }

        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    /**
     * 搜索公开知识库
     *
     * @param keyword 关键词
     * @return 搜索结果
     */
    @Transactional(readOnly = true)
    public List<KnowledgeBaseListItemDTO> searchPublicKnowledgeBases(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listPublicKnowledgeBases(null);
        }

        List<KnowledgeBaseEntity> entities = knowledgeBaseRepository.searchPublicByKeyword(keyword.trim());
        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    /**
     * 根据分类获取公开知识库列表
     *
     * @param category 分类
     * @return 公开知识库列表
     */
    @Transactional(readOnly = true)
    public List<KnowledgeBaseListItemDTO> listPublicKnowledgeBasesByCategory(String category) {
        List<KnowledgeBaseEntity> entities =
                knowledgeBaseRepository.findByIsPublicTrueAndCategoryOrderByUsageCountDesc(category);
        return knowledgeBaseMapper.toListItemDTOList(entities);
    }

    /**
     * 引用公开知识库
     * 其他用户引用公开知识库时，增加引用次数并奖励原分享者积分
     *
     * @param userId 引用者用户ID
     * @param id     知识库ID
     * @return 知识库信息
     */
    @Transactional
    public KnowledgeBaseListItemDTO referenceKnowledgeBase(Long userId, Long id) {
        // 查询公开知识库
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByIdAndIsPublicTrue(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在或未公开"));

        // 检查向量化是否成功完成
        if (entity.getVectorStatus() != interview.guide.modules.knowledgebase.model.VectorStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识库向量化未完成，无法引用");
        }

        // 不能引用自己的公开知识库（不奖励积分）
        boolean isOwnKnowledgeBase = entity.getUserId().equals(userId);

        // 增加引用次数
        entity.incrementUsageCount();
        knowledgeBaseRepository.save(entity);

        log.info("知识库被引用: id={}, userId={}, isOwnKB={}, newUsageCount={}",
                id, userId, isOwnKnowledgeBase, entity.getUsageCount());

        // 如果不是自己的知识库，奖励分享者积分
        if (!isOwnKnowledgeBase) {
            Long ownerUserId = entity.getUserId();
            try {
                // 这里调用 shareKnowledgeBase 来奖励分享者
                // 注意：shareKnowledgeBase 方法内部会检查是否已经领取过该知识库的分享积分
                // 所以重复引用同一知识库不会重复奖励
                pointsService.shareKnowledgeBase(ownerUserId, id);
            } catch (Exception e) {
                // 积分奖励失败不影响主流程，仅记录日志
                log.warn("知识库引用积分奖励失败: kbId={}, ownerUserId={}, error={}",
                        id, ownerUserId, e.getMessage());
            }
        }

        return knowledgeBaseMapper.toListItemDTO(entity);
    }

    /**
     * 获取知识库公开状态
     *
     * @param userId 用户ID
     * @param id     知识库ID
     * @return 是否公开
     */
    @Transactional(readOnly = true)
    public boolean getVisibility(Long userId, Long id) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在或无权操作"));

        return Boolean.TRUE.equals(entity.getIsPublic());
    }

    /**
     * 获取知识库被引用次数
     *
     * @param id 知识库ID
     * @return 被引用次数
     */
    @Transactional(readOnly = true)
    public int getUsageCount(Long id) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));

        return entity.getUsageCount();
    }
}

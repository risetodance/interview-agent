package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库Repository
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    /**
     * 根据文件哈希查找知识库（用于去重）
     */
    Optional<KnowledgeBaseEntity> findByFileHash(String fileHash);

    /**
     * 检查文件哈希是否存在
     */
    boolean existsByFileHash(String fileHash);

    /**
     * 按上传时间倒序查找所有知识库
     */
    List<KnowledgeBaseEntity> findAllByOrderByUploadedAtDesc();

    /**
     * 获取所有不同的分类
     */
    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k WHERE k.category IS NOT NULL ORDER BY k.category")
    List<String> findAllCategories();

    /**
     * 根据分类查找知识库
     */
    List<KnowledgeBaseEntity> findByCategoryOrderByUploadedAtDesc(String category);

    /**
     * 查找未分类的知识库
     */
    List<KnowledgeBaseEntity> findByCategoryIsNullOrderByUploadedAtDesc();

    /**
     * 按名称或文件名模糊搜索（不区分大小写）
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 按文件大小排序
     */
    List<KnowledgeBaseEntity> findAllByOrderByFileSizeDesc();

    /**
     * 按访问次数排序
     */
    List<KnowledgeBaseEntity> findAllByOrderByAccessCountDesc();

    /**
     * 按提问次数排序
     */
    List<KnowledgeBaseEntity> findAllByOrderByQuestionCountDesc();

    // ==================== 批量更新 ====================

    /**
     * 批量增加知识库提问计数
     * @param ids 知识库ID列表
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE KnowledgeBaseEntity k SET k.questionCount = k.questionCount + 1 WHERE k.id IN :ids")
    int incrementQuestionCountBatch(@Param("ids") List<Long> ids);

    /**
     * 批量减少知识库提问计数
     * @param ids 知识库ID列表
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE KnowledgeBaseEntity k SET k.questionCount = k.questionCount - 1 WHERE k.id IN :ids AND k.questionCount > 0")
    int decrementQuestionCountBatch(@Param("ids") List<Long> ids);

    // ==================== 统计查询 ====================

    /**
     * 统计总提问次数
     */
    @Query("SELECT COALESCE(SUM(k.questionCount), 0) FROM KnowledgeBaseEntity k")
    long sumQuestionCount();

    /**
     * 统计总访问次数
     */
    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k")
    long sumAccessCount();

    /**
     * 按向量化状态统计数量
     */
    long countByVectorStatus(VectorStatus vectorStatus);

    /**
     * 按向量化状态查找知识库（按上传时间倒序）
     */
    List<KnowledgeBaseEntity> findByVectorStatusOrderByUploadedAtDesc(VectorStatus vectorStatus);

    // ==================== 用户隔离查询 ====================

    /**
     * 按用户ID查找知识库（按上传时间倒序）
     */
    List<KnowledgeBaseEntity> findByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * 按用户ID查找知识库ID列表
     */
    @Query("SELECT k.id FROM KnowledgeBaseEntity k WHERE k.userId = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    /**
     * 按用户ID和向量化状态查找知识库（按上传时间倒序）
     */
    List<KnowledgeBaseEntity> findByUserIdAndVectorStatusOrderByUploadedAtDesc(Long userId, VectorStatus vectorStatus);

    /**
     * 按用户ID查找知识库（按名称或文件名模糊搜索）
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.userId = :userId AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> searchByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 按用户ID获取分类列表
     */
    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k WHERE k.userId = :userId AND k.category IS NOT NULL ORDER BY k.category")
    List<String> findCategoriesByUserId(@Param("userId") Long userId);

    /**
     * 按用户ID查找知识库（用于下载）
     */
    Optional<KnowledgeBaseEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * 按用户ID和分类查找知识库
     */
    List<KnowledgeBaseEntity> findByUserIdAndCategoryOrderByUploadedAtDesc(Long userId, String category);

    /**
     * 查找用户的未分类知识库
     */
    List<KnowledgeBaseEntity> findByUserIdAndCategoryIsNullOrderByUploadedAtDesc(Long userId);

    /**
     * 按用户ID统计知识库数量
     */
    long countByUserId(Long userId);

    /**
     * 按用户ID和向量化状态统计数量
     */
    long countByUserIdAndVectorStatus(Long userId, VectorStatus vectorStatus);

    // ==================== 用户统计查询 ====================

    /**
     * 按用户ID统计总访问次数
     */
    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k WHERE k.userId = :userId")
    long sumAccessCountByUserId(@Param("userId") Long userId);

    /**
     * 按用户ID统计总存储大小
     */
    @Query("SELECT COALESCE(SUM(k.fileSize), 0) FROM KnowledgeBaseEntity k WHERE k.userId = :userId")
    long sumFileSizeByUserId(@Param("userId") Long userId);

    /**
     * CAS 抢占向量化任务：仅当当前状态在允许集合内才置为 PROCESSING。
     * <p>消费者幂等守卫——relay 重复投递/组内并发重复被此处拦截（返回 0）；
     * COMPLETED 永不重做；PEL 回收路径由调用方在 allowedFrom 中放宽 PROCESSING（僵尸状态复活）。
     *
     * @return 1=抢占成功开始处理；0=任务处理中/已完成/不存在，跳过
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE KnowledgeBaseEntity k SET k.vectorStatus = interview.guide.modules.knowledgebase.model.VectorStatus.PROCESSING, k.vectorError = NULL "
        + "WHERE k.id = :id AND k.vectorStatus IN :allowedFrom")
    int casVectorStatus(@Param("id") Long id, @Param("allowedFrom") List<VectorStatus> allowedFrom);

    /**
     * 定向更新向量化状态（消费者高频调用）。
     * <p>刻意用 UPDATE 语句而不是 findById + save 的读-改-写：后者会把并发期间读到的旧快照
     * 整行回写，可能覆盖并发提交的 content 等字段（lost update）。
     *
     * @return 实际更新行数（知识库不存在时 0）
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE KnowledgeBaseEntity k SET k.vectorStatus = :status, k.vectorError = :error WHERE k.id = :id")
    int updateVectorStatus(@Param("id") Long id, @Param("status") VectorStatus status, @Param("error") String error);
}


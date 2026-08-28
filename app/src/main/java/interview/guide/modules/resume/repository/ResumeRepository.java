package interview.guide.modules.resume.repository;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 简历Repository
 */
@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    /**
     * 根据文件哈希查找简历（用于去重，全局）
     */
    Optional<ResumeEntity> findByFileHash(String fileHash);

    /**
     * 根据用户ID和文件哈希查找简历（用于去重，按用户）
     */
    Optional<ResumeEntity> findByUserIdAndFileHash(Long userId, String fileHash);

    /**
     * 检查文件哈希是否存在
     */
    boolean existsByFileHash(String fileHash);

    /**
     * 根据用户ID查找简历列表（按上传时间倒序，最新在前）
     */
    List<ResumeEntity> findByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * 定向更新分析状态（消费者高频调用）。
     * <p>刻意用 UPDATE 语句而不是 findById + save 的读-改-写：
     * 后者会把并发期间读到的旧快照整行回写，曾覆盖掉重新上传刚提交的 storageKey 等字段（lost update）。
     *
     * @return 实际更新行数（简历不存在时 0）
     */
    @Modifying
    @Transactional
    @Query("UPDATE ResumeEntity r SET r.analyzeStatus = :status, r.analyzeError = :error WHERE r.id = :id")
    int updateAnalyzeStatus(@Param("id") Long id, @Param("status") AsyncTaskStatus status, @Param("error") String error);
}

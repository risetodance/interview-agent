package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.ParentDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Parent 文档 Repository
 * 使用 JPA 直接操作数据库
 */
@Repository
public interface ParentDocumentRepository extends JpaRepository<ParentDocumentEntity, Long> {

    /**
     * 根据 kbId 查询所有 Parent 文档（按 chunk_index 排序）
     */
    List<ParentDocumentEntity> findByKbIdOrderByChunkIndex(Long kbId);

    /**
     * 根据多个 ID 查询 Parent 文档
     */
    List<ParentDocumentEntity> findByIdIn(List<Long> ids);

    /**
     * 根据 kbId 删除所有 Parent 文档
     */
    void deleteByKbId(Long kbId);
}
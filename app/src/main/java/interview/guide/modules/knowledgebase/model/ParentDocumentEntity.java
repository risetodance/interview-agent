package interview.guide.modules.knowledgebase.model;

import interview.guide.modules.knowledgebase.service.SemanticChunkingService;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Parent 段落实体
 * <p>
 * Parent-Child Retrieval 策略中的 Parent 实体。
 * 存储按自然段落（\n\n）切分的完整内容，供检索时返回给用户。
 *
 * <p>
 * 设计说明：
 * <ul>
 *   <li>Parent：完整段落，保留上下文完整性</li>
 *   <li>Child：对 Parent 进一步切分，用于向量检索</li>
 *   <li>检索时：先匹配 Child，再关联到 Parent 返回</li>
 * </ul>
 *
 * @see SemanticChunkingService
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "kb_parent_documents", indexes = {
    @Index(name = "idx_parent_kb_id", columnList = "kb_id"),
    @Index(name = "idx_parent_kb_id_chunk", columnList = "kb_id, chunk_index")
})
public class ParentDocumentEntity {

    /**
     * 主键，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属知识库ID
     */
    @Column(name = "kb_id", nullable = false)
    private Long kbId;

    /**
     * 段落内容（完整文本）
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 在同一知识库内的段落索引（从0开始）
     */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /**
     * 估算的Token数量
     */
    @Column(name = "token_count")
    private Integer tokenCount;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 构造函数
     * @param kbId 知识库ID
     * @param content 段落内容
     * @param chunkIndex 段落索引
     * @param tokenCount 估算的Token数
     */
    public ParentDocumentEntity(Long kbId, String content, Integer chunkIndex, Integer tokenCount) {
        this.kbId = kbId;
        this.content = content;
        this.chunkIndex = chunkIndex;
        this.tokenCount = tokenCount;
        this.createdAt = LocalDateTime.now();
    }
}

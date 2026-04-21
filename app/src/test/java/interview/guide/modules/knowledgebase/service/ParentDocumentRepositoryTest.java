package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.model.ParentDocumentEntity;
import interview.guide.modules.knowledgebase.repository.ParentDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ParentDocumentRepositoryTest {

    @Autowired
    private ParentDocumentRepository repository;

    @Test
    void testBatchInsertAndFind() {
        Long kbId = 99999L;

        // 清理旧数据
        repository.deleteByKbId(kbId);

        // 批量插入
        List<ParentDocumentEntity> parents = List.of(
                new ParentDocumentEntity(kbId, "Parent 1 content", 0, 5),
                new ParentDocumentEntity(kbId, "Parent 2 content", 1, 6),
                new ParentDocumentEntity(kbId, "Parent 3 content", 2, 7)
        );
        repository.saveAll(parents);

        // 查询验证
        List<ParentDocumentEntity> found = repository.findByKbIdOrderByChunkIndex(kbId);
        assertEquals(3, found.size());
        assertEquals("Parent 1 content", found.get(0).getContent());

        // 清理
        repository.deleteByKbId(kbId);
    }

    @Test
    void testFindByIds() {
        Long kbId = 99998L;
        repository.deleteByKbId(kbId);

        List<ParentDocumentEntity> parents = List.of(
                new ParentDocumentEntity(kbId, "Content A", 0, 5),
                new ParentDocumentEntity(kbId, "Content B", 1, 6)
        );
        repository.saveAll(parents);

        List<ParentDocumentEntity> found = repository.findByKbIdOrderByChunkIndex(kbId);
        assertEquals(2, found.size());

        // 按 ID 查询
        List<Long> ids = found.stream().map(ParentDocumentEntity::getId).toList();
        List<ParentDocumentEntity> byIds = repository.findByIdIn(ids);
        assertEquals(2, byIds.size());

        repository.deleteByKbId(kbId);
    }
}
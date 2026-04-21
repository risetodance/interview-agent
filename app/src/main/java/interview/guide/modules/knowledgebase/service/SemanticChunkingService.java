package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.model.ParentDocumentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 语义分块服务
 * 实现 Parent-Child Retrieval 分块策略：
 * - Parent: 按自然段落 (\n\n) 切分，限制 800-1500 Token
 * - Child: 从 Parent 按句子/换行进一步切分
 * - 仅对 Child 向量化，Parent 存入独立表
 */
@Slf4j
@Service
public class SemanticChunkingService {

    private static final int PARENT_MAX_TOKENS = 1200;
    private static final int CHILD_MAX_TOKENS = 300;
    private static final int CHUNK_SIZE = PARENT_MAX_TOKENS * 2;

    public List<ParentDocumentEntity> splitIntoParents(Long kbId, String content) {
        List<ParentDocumentEntity> parents = new ArrayList<>();

        // 边界条件：空内容直接返回
        if (content == null || content.isBlank()) {
            return parents;
        }

        // 按自然段落切分（双换行符分隔）
        String[] paragraphs = content.split("\n\n");

        int chunkIndex = 0;
        for (String paragraph : paragraphs) {
            // 跳过空段落
            if (paragraph.isBlank()) {
                continue;
            }

            // 估算段落token数
            int paragraphTokens = estimateTokens(paragraph);

            // 小于等于最大token限制，直接作为parent
            if (paragraphTokens <= PARENT_MAX_TOKENS) {
                parents.add(new ParentDocumentEntity(kbId, paragraph.trim(), chunkIndex++, paragraphTokens));
            } else {
                // 超过限制，需要进一步切分
                parents.addAll(splitLargeParagraph(kbId, paragraph, chunkIndex));
                chunkIndex = parents.size();
            }
        }

        log.info("Parent 切分完成: kbId={}, parentCount={}", kbId, parents.size());
        return parents;
    }

    /**
     * 切分超大段落
     * <p>
     * 策略：按行切分，累积多行直到达到最大token限制。
     * 如果单行就超过限制，则使用硬切分（固定长度）
     */
    private List<ParentDocumentEntity> splitLargeParagraph(Long kbId, String paragraph, int startIndex) {
        List<ParentDocumentEntity> parents = new ArrayList<>();

        String[] lines = paragraph.split("\n");
        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;
        int chunkIndex = startIndex;

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }

            int lineTokens = estimateTokens(line);

            // 累加该行不超过限制，加入buffer
            if (bufferTokens + lineTokens <= PARENT_MAX_TOKENS) {
                if (!buffer.isEmpty()) {
                    buffer.append("\n");
                }
                buffer.append(line);
                bufferTokens += lineTokens;
            } else {
                // 超过限制，先输出当前buffer
                if (!buffer.isEmpty()) {
                    String bufferStr = buffer.toString().trim();
                    parents.add(new ParentDocumentEntity(kbId, bufferStr, chunkIndex++, estimateTokens(bufferStr)));
                    buffer = new StringBuilder();
                    bufferTokens = 0;
                }

                // 单行就超过限制：硬切分
                if (lineTokens > PARENT_MAX_TOKENS) {
                    parents.addAll(hardSplit(kbId, line, chunkIndex));
                    chunkIndex = parents.size();
                } else {
                    // 单行可以接受，作为新的buffer起点
                    buffer.append(line);
                    bufferTokens = lineTokens;
                }
            }
        }

        // 处理最后剩余的buffer
        if (!buffer.isEmpty()) {
            String bufferStr = buffer.toString().trim();
            parents.add(new ParentDocumentEntity(kbId, bufferStr, chunkIndex, estimateTokens(bufferStr)));
        }

        return parents;
    }

    /**
     * 硬切分：按固定长度切分超长文本
     * <p>
     * 用于处理单个句子/段落就超过token限制的情况。
     * 使用固定字符数切分（CHUNK_SIZE = PARENT_MAX_TOKENS * 2）
     */
    private List<ParentDocumentEntity> hardSplit(Long kbId, String text, int startIndex) {
        List<ParentDocumentEntity> parents = new ArrayList<>();
        int chunkIndex = startIndex;

        for (int i = 0; i < text.length(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, text.length());
            String chunk = text.substring(i, end).trim();
            parents.add(new ParentDocumentEntity(kbId, chunk, chunkIndex++, estimateTokens(chunk)));
        }

        return parents;
    }

    public List<Document> splitIntoChildren(List<ParentDocumentEntity> parents) {
        List<Document> children = new ArrayList<>();

        // 遍历每个parent，切分出child
        for (ParentDocumentEntity parent : parents) {
            children.addAll(splitParentIntoChildren(parent));
        }

        log.info("Child 切分完成: parentCount={}, childCount={}", parents.size(), children.size());
        return children;
    }

    /**
     * 将Parent切分为Child
     * <p>
     * 策略：按句子/换行切分，累积多个句子直到达到CHILD_MAX_TOKENS限制。
     * Child用于向量检索，通过parent_id关联回Parent获取完整上下文。
     */
    private List<Document> splitParentIntoChildren(ParentDocumentEntity parent) {
        List<Document> children = new ArrayList<>();
        String content = parent.getContent();

        if (content == null || content.isBlank()) {
            return children;
        }

        // 按句子分隔符切分：中文句号/感叹号/问号，或换行
        String[] sentences = content.split("(?<=[。！？.!?])|(?<=\n)");

        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;

        for (String sentence : sentences) {
            if (sentence.isBlank()) {
                continue;
            }

            int sentenceTokens = estimateTokens(sentence);

            // 累加句子不超过限制，加入buffer
            if (bufferTokens + sentenceTokens <= CHILD_MAX_TOKENS) {
                if (!buffer.isEmpty()) {
                    buffer.append(" ");
                }
                buffer.append(sentence.trim());
                bufferTokens += sentenceTokens;
            } else {
                // 超过限制，先输出当前buffer
                if (!buffer.isEmpty()) {
                    children.add(createChildDocument(buffer.toString(), parent));
                    buffer = new StringBuilder();
                    bufferTokens = 0;
                }

                // 单句子超过限制：直接添加
                if (sentenceTokens > CHILD_MAX_TOKENS) {
                    children.add(createChildDocument(sentence.trim(), parent));
                } else {
                    // 新的句子加入buffer
                    buffer.append(sentence.trim());
                    bufferTokens = sentenceTokens;
                }
            }
        }

        // 处理最后剩余的buffer
        if (!buffer.isEmpty()) {
            children.add(createChildDocument(buffer.toString(), parent));
        }

        // 边界条件：确保至少有一个child
        if (children.isEmpty()) {
            children.add(createChildDocument(content, parent));
        }

        return children;
    }

    /**
     * 创建Child文档
     * <p>
     * metadata中包含kb_id、parent_id、chunk_index，
     * 用于检索后关联回Parent获取完整段落内容
     */
    private Document createChildDocument(String text, ParentDocumentEntity parent) {
        return Document.builder()
                .text(text)
                .metadata(Map.of(
                        "kb_id", parent.getKbId().toString(),
                        "parent_id", parent.getId().toString(),
                        "chunk_index", String.valueOf(parent.getChunkIndex())
                ))
                .build();
    }

    /**
     * 估算文本token数
     * <p>
     * 计算规则：
     * - 中文字符：每2个字符算1个token
     * - 非中文字符：每4个字符算1个token
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        for (int i = 0; i < text.length(); i++) {
            // 中文字符范围：0x4e00-0x9fa5
            if (text.charAt(i) >= 0x4e00 && text.charAt(i) <= 0x9fa5) {
                chineseCount++;
            }
        }

        int nonChineseCount = text.length() - chineseCount;
        return (int) Math.ceil(chineseCount / 2.0 + nonChineseCount / 4.0);
    }
}

package interview.guide.modules.knowledgebase.service;

import interview.guide.common.config.ChunkingProperties;
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
 * - Parent: 按自然段落 (\n\n) 切分，相邻小段落合并，限制 800-1500 Token
 * - 相邻 Parent 块之间保留 100~200 tokens 重叠，防止语义断裂
 * - Child: 从 Parent 按句子/换行进一步切分
 * - 仅对 Child 向量化，Parent 存入独立表
 */
@Slf4j
@Service
public class SemanticChunkingService {

    private final ChunkingProperties chunkingProperties;

    public SemanticChunkingService(ChunkingProperties chunkingProperties) {
        this.chunkingProperties = chunkingProperties;
    }

    public List<ParentDocumentEntity> splitIntoParents(Long kbId, String content) {
        List<ParentDocumentEntity> parents = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return parents;
        }

        String[] paragraphs = content.split("\n\n");
        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;
        int chunkIndex = 0;
        int maxTokens = chunkingProperties.getParentMaxTokens();

        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                continue;
            }

            int paragraphTokens = estimateTokens(paragraph);

            // 大段落：超过限制，需要切分
            if (paragraphTokens > maxTokens) {
                // 先输出当前buffer
                if (bufferTokens > 0) {
                    String bufferStr = buffer.toString().trim();
                    parents.add(new ParentDocumentEntity(kbId, bufferStr, chunkIndex++, bufferTokens));
                    buffer = new StringBuilder();
                    bufferTokens = 0;
                }
                // 切分大段落
                parents.addAll(splitLargeParagraph(kbId, paragraph, chunkIndex, maxTokens));
                chunkIndex = parents.size();
            } else {
                // 小段落：尝试加入buffer
                if (bufferTokens + paragraphTokens <= maxTokens) {
                    // 可以合并
                    if (bufferTokens > 0) {
                        buffer.append("\n\n");
                    }
                    buffer.append(paragraph.trim());
                    bufferTokens += paragraphTokens;
                } else {
                    // 超过限制，输出当前buffer，开启新buffer
                    if (bufferTokens > 0) {
                        String bufferStr = buffer.toString().trim();
                        parents.add(new ParentDocumentEntity(kbId, bufferStr, chunkIndex++, bufferTokens));
                    }
                    buffer = new StringBuilder();
                    buffer.append(paragraph.trim());
                    bufferTokens = paragraphTokens;
                }
            }
        }

        // 处理最后剩余的buffer
        if (bufferTokens > 0) {
            String bufferStr = buffer.toString().trim();
            parents.add(new ParentDocumentEntity(kbId, bufferStr, chunkIndex, bufferTokens));
        }

        // 添加相邻块之间的 overlap，防止语义断裂
        addOverlapBetweenParents(parents);

        log.info("Parent 切分完成: kbId={}, parentCount={}", kbId, parents.size());
        return parents;
    }

    /**
     * 在相邻 Parent 块之间添加双向 overlap
     * <p>
     * - 当前块末尾添加下一个块开头的 100~200 tokens
     * - 下一个块开头添加当前块末尾的 100~200 tokens
     * 防止在语义边界处断裂。
     */
    private void addOverlapBetweenParents(List<ParentDocumentEntity> parents) {
        if (parents == null || parents.size() < 2) {
            return;
        }

        int overlapMinTokens = 100;
        int overlapMaxTokens = 200;
        int targetOverlapTokens = (overlapMinTokens + overlapMaxTokens) / 2;

        for (int i = 0; i < parents.size() - 1; i++) {
            ParentDocumentEntity current = parents.get(i);
            ParentDocumentEntity next = parents.get(i + 1);

            // 当前块末尾添加下一个块开头的 overlap
            String leadingOverlap = extractLeadingTokens(next.getContent(), targetOverlapTokens);
            String newCurrentContent = current.getContent() + "\n\n" + leadingOverlap;
            current.setContent(newCurrentContent);
            current.setTokenCount(estimateTokens(newCurrentContent));

            // 下一个块开头添加当前块末尾的 overlap
            String trailingOverlap = extractTrailingTokens(current.getContent(), targetOverlapTokens);
            String newNextContent = trailingOverlap + "\n\n" + next.getContent();
            next.setContent(newNextContent);
            next.setTokenCount(estimateTokens(newNextContent));
        }
    }

    /**
     * 从文本开头提取指定数量的 tokens
     */
    private String extractLeadingTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int currentTokens = 0;
        int chineseCount = 0;

        for (int i = 0; i < text.length() && currentTokens < maxTokens; i++) {
            char c = text.charAt(i);
            result.append(c);

            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseCount++;
            }
            int nonChineseCount = result.length() - chineseCount;
            currentTokens = (int) Math.ceil(chineseCount / 2.0 + nonChineseCount / 4.0);
        }

        return result.toString().trim();
    }

    /**
     * 从文本末尾提取指定数量的 tokens
     */
    private String extractTrailingTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int currentTokens = 0;
        int chineseCount = 0;

        // 从文本末尾开始收集字符
        for (int i = text.length() - 1; i >= 0 && currentTokens < maxTokens; i--) {
            char c = text.charAt(i);
            result.insert(0, c); // 头部插入，保持正序

            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseCount++;
            }
            int nonChineseCount = result.length() - chineseCount;
            currentTokens = (int) Math.ceil(chineseCount / 2.0 + nonChineseCount / 4.0);
        }

        return result.toString().trim();
    }

    /**
     * 切分超大段落
     * <p>
     * 策略：按行切分，累积多行直到达到最大token限制。
     * 如果单行就超过限制，则使用硬切分（固定长度）
     */
    private List<ParentDocumentEntity> splitLargeParagraph(Long kbId, String paragraph, int startIndex, int maxTokens) {
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

            if (bufferTokens + lineTokens <= maxTokens) {
                if (!buffer.isEmpty()) {
                    buffer.append("\n");
                }
                buffer.append(line);
                bufferTokens += lineTokens;
            } else {
                if (bufferTokens > 0) {
                    String bufferStr = buffer.toString().trim();
                    parents.add(new ParentDocumentEntity(kbId, bufferStr, chunkIndex++, estimateTokens(bufferStr)));
                    buffer = new StringBuilder();
                    bufferTokens = 0;
                }

                if (lineTokens > maxTokens) {
                    parents.addAll(hardSplit(kbId, line, chunkIndex, maxTokens));
                    chunkIndex = parents.size();
                } else {
                    buffer.append(line);
                    bufferTokens = lineTokens;
                }
            }
        }

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
     * 使用固定字符数切分（maxTokens * 2）
     */
    private List<ParentDocumentEntity> hardSplit(Long kbId, String text, int startIndex, int maxTokens) {
        List<ParentDocumentEntity> parents = new ArrayList<>();
        int chunkIndex = startIndex;
        int chunkSize = maxTokens * 2;

        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end).trim();
            if (!chunk.isEmpty()) {
                parents.add(new ParentDocumentEntity(kbId, chunk, chunkIndex++, estimateTokens(chunk)));
            }
        }

        return parents;
    }

    public List<Document> splitIntoChildren(List<ParentDocumentEntity> parents) {
        List<Document> children = new ArrayList<>();

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
        int maxTokens = chunkingProperties.getChildMaxTokens();

        for (String sentence : sentences) {
            if (sentence.isBlank()) {
                continue;
            }

            int sentenceTokens = estimateTokens(sentence);

            // 累加句子不超过限制，加入buffer
            if (bufferTokens + sentenceTokens <= maxTokens) {
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
                if (sentenceTokens > maxTokens) {
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
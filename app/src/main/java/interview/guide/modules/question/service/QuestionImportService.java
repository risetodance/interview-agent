package interview.guide.modules.question.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.question.enums.QuestionDifficulty;
import interview.guide.modules.question.model.QuestionDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 题目导入服务
 * 支持 Excel (.xlsx, .xls) 和 Markdown (.md) 格式
 */
@Slf4j
@Service
public class QuestionImportService {

    private static final String EXCEL_CONTENT = "题目内容";
    private static final String EXCEL_ANSWER = "答案";
    private static final String EXCEL_DIFFICULTY = "难度";
    private static final String EXCEL_TAGS = "标签";

    /**
     * 解析 Excel 文件导入题目
     *
     * @param file Excel 文件
     * @return 题目列表
     */
    public List<QuestionDTO> parseExcel(MultipartFile file) {
        log.info("开始解析 Excel 文件: {}", file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<QuestionDTO> questions = new ArrayList<>();

            // 获取表头
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件格式错误：缺少表头");
            }

            int contentIndex = -1;
            int answerIndex = -1;
            int difficultyIndex = -1;
            int tagsIndex = -1;

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell == null) continue;

                String header = getCellValueAsString(cell).trim();
                if (EXCEL_CONTENT.equals(header)) {
                    contentIndex = i;
                } else if (EXCEL_ANSWER.equals(header)) {
                    answerIndex = i;
                } else if (EXCEL_DIFFICULTY.equals(header)) {
                    difficultyIndex = i;
                } else if (EXCEL_TAGS.equals(header)) {
                    tagsIndex = i;
                }
            }

            if (contentIndex == -1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件格式错误：缺少'" + EXCEL_CONTENT + "'列");
            }

            // 解析数据行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell contentCell = row.getCell(contentIndex);
                if (contentCell == null || getCellValueAsString(contentCell).trim().isEmpty()) {
                    continue; // 跳过空行
                }

                String content = getCellValueAsString(contentCell).trim();
                String answer = answerIndex != -1 ? getCellValueAsString(row.getCell(answerIndex)).trim() : "";
                QuestionDifficulty difficulty = parseDifficulty(difficultyIndex != -1 ?
                        getCellValueAsString(row.getCell(difficultyIndex)) : "MEDIUM");
                List<String> tags = parseTags(tagsIndex != -1 ?
                        getCellValueAsString(row.getCell(tagsIndex)) : "");

                QuestionDTO dto = QuestionDTO.builder()
                        .content(content)
                        .answer(answer)
                        .difficulty(difficulty)
                        .tags(tags)
                        .build();

                questions.add(dto);
            }

            log.info("Excel 文件解析完成，共解析 {} 道题目", questions.size());
            return questions;

        } catch (IOException e) {
            log.error("Excel 文件解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 Markdown 文件导入题目
     *
     * @param content Markdown 文件内容
     * @return 题目列表
     */
    public List<QuestionDTO> parseMarkdown(String content) {
        log.info("开始解析 Markdown 内容");

        List<QuestionDTO> questions = new ArrayList<>();
        String[] lines = content.split("\n");

        QuestionDTO currentQuestion = null;
        StringBuilder currentContent = new StringBuilder();
        StringBuilder currentAnswer = new StringBuilder();
        String currentDifficulty = "MEDIUM";
        List<String> currentTags = new ArrayList<>();

        boolean inAnswer = false;

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("## 题目") || line.startsWith("### Q")) {
                // 保存上一道题目
                if (currentQuestion != null) {
                    currentQuestion.setContent(currentContent.toString().trim());
                    currentQuestion.setAnswer(currentAnswer.toString().trim());
                    currentQuestion.setDifficulty(parseDifficulty(currentDifficulty));
                    currentQuestion.setTags(currentTags.isEmpty() ? null : currentTags);
                    questions.add(currentQuestion);
                }

                // 开始新题目
                currentQuestion = new QuestionDTO();
                currentContent = new StringBuilder();
                currentAnswer = new StringBuilder();
                currentDifficulty = "MEDIUM";
                currentTags = new ArrayList<>();
                inAnswer = false;

            } else if (line.startsWith("### 答案") || line.startsWith("**答案**") || line.startsWith("## 答案")) {
                inAnswer = true;
            } else if (line.startsWith("### 难度") || line.startsWith("**难度**") || line.startsWith("## 难度")) {
                String difficulty = line.replaceAll(".*[:：]", "").trim();
                currentDifficulty = difficulty;
            } else if (line.startsWith("### 标签") || line.startsWith("**标签**") || line.startsWith("## 标签")) {
                String tagsStr = line.replaceAll(".*[:：]", "").trim();
                currentTags = parseTags(tagsStr);
            } else if (currentQuestion != null) {
                if (inAnswer) {
                    currentAnswer.append(line).append("\n");
                } else {
                    currentContent.append(line).append("\n");
                }
            }
        }

        // 保存最后一道题目
        if (currentQuestion != null && currentContent.length() > 0) {
            currentQuestion.setContent(currentContent.toString().trim());
            currentQuestion.setAnswer(currentAnswer.toString().trim());
            currentQuestion.setDifficulty(parseDifficulty(currentDifficulty));
            currentQuestion.setTags(currentTags.isEmpty() ? null : currentTags);
            questions.add(currentQuestion);
        }

        // 兼容更简单的 Markdown 格式：每两个段落为一题（问题和答案）
        if (questions.isEmpty()) {
            questions = parseSimpleMarkdown(content);
        }

        log.info("Markdown 解析完成，共解析 {} 道题目", questions.size());
        return questions;
    }

    /**
     * 解析简单 Markdown 格式
     * 格式：Q: 问题内容\n\nA: 答案内容
     */
    private List<QuestionDTO> parseSimpleMarkdown(String content) {
        List<QuestionDTO> questions = new ArrayList<>();
        String[] blocks = content.split("\n\n(?=Q[:：]|题目)");

        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            String[] lines = block.split("\n");
            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder answerBuilder = new StringBuilder();
            String difficulty = "MEDIUM";
            List<String> tags = new ArrayList<>();

            boolean inAnswer = false;

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.matches("^[QqQq][:：].*") || line.matches("^[0-9]+[.、].*")) {
                    if (contentBuilder.length() > 0 && answerBuilder.length() > 0) {
                        // 保存上一题
                        QuestionDTO dto = QuestionDTO.builder()
                                .content(contentBuilder.toString().trim())
                                .answer(answerBuilder.toString().trim())
                                .difficulty(parseDifficulty(difficulty))
                                .tags(tags.isEmpty() ? null : tags)
                                .build();
                        questions.add(dto);
                        contentBuilder = new StringBuilder();
                        answerBuilder = new StringBuilder();
                        difficulty = "MEDIUM";
                        tags = new ArrayList<>();
                    }
                    contentBuilder.append(line.replaceFirst("^[QqQq][:：.、].*", "").trim());
                    inAnswer = false;
                } else if (line.matches("^[Aa][:：].*")) {
                    answerBuilder.append(line.replaceFirst("^[Aa][:：].*", "").trim());
                    inAnswer = true;
                } else if (line.toLowerCase().contains("难度")) {
                    difficulty = line.replaceAll(".*[:：]", "").trim();
                } else if (line.startsWith("#") || line.startsWith("[")) {
                    // 标签行
                    tags = parseTags(line);
                } else if (inAnswer) {
                    answerBuilder.append("\n").append(line);
                } else {
                    contentBuilder.append("\n").append(line);
                }
            }

            // 保存最后一道题
            if (contentBuilder.length() > 0) {
                QuestionDTO dto = QuestionDTO.builder()
                        .content(contentBuilder.toString().trim())
                        .answer(answerBuilder.toString().trim())
                        .difficulty(parseDifficulty(difficulty))
                        .tags(tags.isEmpty() ? null : tags)
                        .build();
                questions.add(dto);
            }
        }

        return questions;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                yield String.valueOf((int) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private QuestionDifficulty parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isEmpty()) {
            return QuestionDifficulty.MEDIUM;
        }

        String normalized = difficulty.trim().toUpperCase();
        if (normalized.contains("简单") || normalized.contains("EASY")) {
            return QuestionDifficulty.EASY;
        } else if (normalized.contains("困难") || normalized.contains("HARD")) {
            return QuestionDifficulty.HARD;
        }
        return QuestionDifficulty.MEDIUM;
    }

    private List<String> parseTags(String tagsStr) {
        if (tagsStr == null || tagsStr.isEmpty()) {
            return new ArrayList<>();
        }

        // 支持多种分隔符：逗号、顿号、分号、顿号
        String normalized = tagsStr.replaceAll("[，。；;、]", ",");
        return Arrays.asList(normalized.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}

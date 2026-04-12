package interview.guide.modules.question.service;

import interview.guide.common.config.QuestionImportConfigProperties;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.service.DifficultyAdjustmentService.Difficulty;
import interview.guide.modules.question.model.QuestionDTO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 题目导入服务
 * 支持 Excel (.xlsx, .xls) 和 Markdown (.md) 格式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportService {

    private final QuestionImportConfigProperties configProperties;

    private static final String EXCEL_CONTENT = "题目内容";
    private static final String EXCEL_ANSWER = "答案";
    private static final String EXCEL_DIFFICULTY = "难度(基础/进阶/专家)";
    private static final String EXCEL_TAGS = "标签(逗号分隔)";

    /**
     * 解析 Excel 文件导入题目
     *
     * @param file Excel 文件
     * @return 题目列表
     */
    public List<QuestionDTO> parseExcel(MultipartFile file) {
        log.info("开始解析 Excel 文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件格式错误：无法读取工作表");
            }

            List<QuestionDTO> questions = new ArrayList<>();

            // 获取表头
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件格式错误：缺少表头");
            }

            // 收集所有表头并验证
            // 支持带提示的表头：题目内容、答案、难度(基础/进阶/专家)、标签(逗号分隔)
            List<String> fileHeaders = new ArrayList<>();
            int contentIndex = -1;
            int answerIndex = -1;
            int difficultyIndex = -1;
            int tagsIndex = -1;

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell == null) continue;

                String header = getCellValueAsString(cell).trim();
                if (header.isEmpty()) continue;

                // 验证表头是否有效（支持带提示的表头）
                boolean isValid = header.startsWith("题目内容") || header.startsWith("答案") ||
                        header.startsWith("难度") || header.startsWith("标签");
                if (!isValid) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "Excel 表头包含无效列 '" + header + "'，请使用标准模板。标准表头：题目内容、答案、难度(基础/进阶/专家)、标签(逗号分隔)");
                }

                fileHeaders.add(header);

                if (header.startsWith("题目内容")) {
                    contentIndex = i;
                } else if (header.startsWith("答案")) {
                    answerIndex = i;
                } else if (header.startsWith("难度")) {
                    difficultyIndex = i;
                } else if (header.startsWith("标签")) {
                    tagsIndex = i;
                }
            }

            // 必须包含"题目内容"列
            if (contentIndex == -1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件格式错误：缺少'题目内容'列");
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
                Difficulty difficulty = parseDifficulty(difficultyIndex != -1 ?
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

            // 校验导入数量
            int maxCount = configProperties.getMaxCount();
            if (questions.size() > maxCount) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "导入题目数量（" + questions.size() + "）超过限制（" + maxCount + "），请分批导入");
            }

            log.info("Excel 文件解析完成，共解析 {} 道题目", questions.size());
            return questions;

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Excel 文件解析失败（IO错误）: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件读取失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Excel 文件解析失败（未知错误）: {}", e.getMessage(), e);
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

        // 校验导入数量
        int maxCount = configProperties.getMaxCount();
        if (questions.size() > maxCount) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "导入题目数量（" + questions.size() + "）超过限制（" + maxCount + "），请分批导入");
        }

        log.info("Markdown 解析完成，共解析 {} 道题目", questions.size());
        return questions;
    }

    /**
     * 生成 Excel 导入模板
     *
     * @param response HTTP 响应
     */
    public void generateTemplate(HttpServletResponse response) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("题目导入模板");

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);

            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {EXCEL_CONTENT, EXCEL_ANSWER, EXCEL_DIFFICULTY, EXCEL_TAGS};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            // 设置列宽
            sheet.setColumnWidth(0, 12000);  // 题目内容
            sheet.setColumnWidth(1, 8000);   // 答案
            sheet.setColumnWidth(2, 4000);   // 难度
            sheet.setColumnWidth(3, 6000);  // 标签

            // 冻结首行
            sheet.createFreezePane(0, 1);

            // 先写入 ByteArrayOutputStream
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            workbook.write(baos);
            byte[] bytes = baos.toByteArray();

            // 响应设置
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String filename = URLEncoder.encode("题目导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"; filename*=utf-8''" + filename);
            response.setContentLengthLong(bytes.length);

            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();

            log.info("Excel 模板生成成功");
        }
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

    private Difficulty parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isEmpty()) {
            return Difficulty.BASIC;
        }

        String normalized = difficulty.trim().toUpperCase();
        if (normalized.contains("简单") || normalized.contains("EASY") || normalized.contains("基础")) {
            return Difficulty.BASIC;
        } else if (normalized.contains("困难") || normalized.contains("HARD") || normalized.contains("专家")) {
            return Difficulty.EXPERT;
        }
        return Difficulty.ADVANCED;
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

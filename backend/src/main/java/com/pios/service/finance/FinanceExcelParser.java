package com.pios.service.finance;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Component
public class FinanceExcelParser {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<String> REQUIRED_HEADERS = List.of(
            "날짜", "자산", "분류", "소분류", "내용", "금액(원)", "수입/지출", "메모", "화폐"
    );

    public List<ParsedFinanceRow> parse(MultipartFile file) {
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheet("내역");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> headers = readHeaders(headerRow);
            for (String required : REQUIRED_HEADERS) {
                if (!headers.containsKey(required)) {
                    throw new IllegalArgumentException("Missing required column: " + required);
                }
            }

            DataFormatter formatter = new DataFormatter(Locale.KOREA);
            List<ParsedFinanceRow> rows = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row, headers, formatter)) continue;

                Instant transactionAt = readInstant(row.getCell(headers.get("날짜")), formatter);
                LocalDate transactionDate = transactionAt.atZone(SEOUL).toLocalDate();
                String asset = readString(row, headers, "자산", formatter);
                String category = readString(row, headers, "분류", formatter);
                String subcategory = readString(row, headers, "소분류", formatter);
                String description = readString(row, headers, "내용", formatter);
                BigDecimal amount = readMoney(row.getCell(headers.get("금액(원)")), formatter);
                String flowType = readString(row, headers, "수입/지출", formatter);
                String memo = readString(row, headers, "메모", formatter);
                String currency = readString(row, headers, "화폐", formatter);
                if (currency == null || currency.isBlank()) currency = "KRW";

                Map<String, Object> sourceRow = new LinkedHashMap<>();
                sourceRow.put("날짜", transactionAt.toString());
                sourceRow.put("자산", asset);
                sourceRow.put("분류", category);
                sourceRow.put("소분류", subcategory);
                sourceRow.put("내용", description);
                sourceRow.put("금액(원)", amount);
                sourceRow.put("수입/지출", flowType);
                sourceRow.put("메모", memo);
                sourceRow.put("화폐", currency);

                String fingerprint = fingerprint(transactionAt, asset, category, subcategory, description, amount, flowType, memo, currency);
                String paymentMethod = "소액결제".equals(asset) ? "소액결제" : asset;
                boolean cashflowIncluded = !"소액결제".equals(asset);
                boolean spendingIncluded = !"통신비".equals(subcategory) || "소액결제".equals(asset);
                if ("이체지출".equals(flowType)) {
                    spendingIncluded = false;
                }

                rows.add(new ParsedFinanceRow(
                        i + 1,
                        transactionAt,
                        transactionDate,
                        asset,
                        category,
                        subcategory,
                        description,
                        amount,
                        flowType,
                        memo,
                        currency,
                        fingerprint,
                        cashflowIncluded,
                        spendingIncluded,
                        paymentMethod,
                        sourceRow
                ));
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse finance excel file: " + e.getMessage(), e);
        }
    }

    private Map<String, Integer> readHeaders(Row row) {
        if (row == null) throw new IllegalArgumentException("Header row not found");
        Map<String, Integer> headers = new HashMap<>();
        for (Cell cell : row) {
            String value = cell.getStringCellValue();
            if (value != null && !value.isBlank()) {
                headers.put(value.trim(), cell.getColumnIndex());
            }
        }
        return headers;
    }

    private boolean isBlankRow(Row row, Map<String, Integer> headers, DataFormatter formatter) {
        return REQUIRED_HEADERS.stream()
                .map(headers::get)
                .filter(Objects::nonNull)
                .map(row::getCell)
                .map(cell -> formatter.formatCellValue(cell).trim())
                .allMatch(String::isBlank);
    }

    private String readString(Row row, Map<String, Integer> headers, String name, DataFormatter formatter) {
        Integer idx = headers.get(name);
        if (idx == null) return "";
        return formatter.formatCellValue(row.getCell(idx)).trim();
    }

    private BigDecimal readMoney(Cell cell, DataFormatter formatter) {
        if (cell == null) return BigDecimal.ZERO;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(0, java.math.RoundingMode.HALF_UP);
        }
        String text = formatter.formatCellValue(cell).replace(",", "").trim();
        if (text.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(text);
    }

    private Instant readInstant(Cell cell, DataFormatter formatter) {
        if (cell == null) throw new IllegalArgumentException("Date cell is empty");
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime local = cell.getLocalDateTimeCellValue();
            return local.atZone(SEOUL).toInstant();
        }
        String text = formatter.formatCellValue(cell).trim();
        if (text.isBlank()) throw new IllegalArgumentException("Date value is empty");
        try {
            return LocalDateTime.parse(text.replace(" ", "T")).atZone(SEOUL).toInstant();
        } catch (Exception ignored) {
            return LocalDate.parse(text.substring(0, 10)).atStartOfDay(SEOUL).toInstant();
        }
    }

    private String fingerprint(Instant transactionAt, String asset, String category, String subcategory,
                               String description, BigDecimal amount, String flowType, String memo, String currency) throws Exception {
        String normalized = String.join("|",
                transactionAt.toString(),
                normalize(asset),
                normalize(category),
                normalize(subcategory),
                normalize(description),
                amount.stripTrailingZeros().toPlainString(),
                normalize(flowType),
                normalize(memo),
                normalize(currency)
        );
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

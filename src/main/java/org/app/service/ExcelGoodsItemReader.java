package org.app.service;

import org.app.logging.AppLogger;
import org.app.logging.NoOpLogger;
import org.app.model.GoodsItem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcelGoodsItemReader {
    private static final String HEADER_HS_CODE = "HS CODE";
    private static final String HEADER_KG = "kg";
    private static final String HEADER_VALUE = "Valoare (Value)";
    private static final String HEADER_PARCELS = "colete (parcels no.)";
    private static final int HEADER_SCAN_LIMIT = 50;

    private static final Pattern LEADING_DIGITS = Pattern.compile("^(\\d+)");
    private final AppLogger logger;

    public ExcelGoodsItemReader(AppLogger logger) {
        this.logger = logger == null ? new NoOpLogger() : logger;
    }

    public List<GoodsItem> read(File excelFile) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(excelFile)) {
            DataFormatter formatter = new DataFormatter(Locale.US, true);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);

            HeaderInfo headerInfo = findHeader(sheet, formatter, evaluator);
            Map<String, Integer> columns = headerInfo.columns;
            logger.info("Antet gasit pe randul " + (headerInfo.rowIndex + 1) + ".");

            List<GoodsItem> items = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int rowIndex = headerInfo.rowIndex + 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String hsValue = cellText(row, columns.get(HEADER_HS_CODE), formatter, evaluator);
                if (hsValue == null) {
                    continue;
                }
                String trimmedHs = hsValue.trim();
                if (!isHsCodeTotal(trimmedHs)) {
                    continue;
                }

                String hsCode = extractHsCode(trimmedHs);
                if (hsCode == null) {
                    continue;
                }

                String kgText = safeNumericText(cellText(row, columns.get(HEADER_KG), formatter, evaluator));
                String valueText = safeNumericText(cellText(row, columns.get(HEADER_VALUE), formatter, evaluator));
                String parcelsText = safeNumericText(cellText(row, columns.get(HEADER_PARCELS), formatter, evaluator));

                BigDecimal valueAmount = parseBigDecimal(valueText);
                GoodsItem item = new GoodsItem(hsCode, kgText, valueText, valueAmount, parcelsText);
                items.add(item);
            }

            return items;
        }
    }

    private HeaderInfo findHeader(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int maxRow = Math.min(sheet.getLastRowNum(), HEADER_SCAN_LIMIT);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Map<String, Integer> columns = resolveColumns(row, formatter, evaluator);
            if (columns.containsKey(HEADER_HS_CODE)
                    && columns.containsKey(HEADER_KG)
                    && columns.containsKey(HEADER_VALUE)
                    && columns.containsKey(HEADER_PARCELS)) {
                return new HeaderInfo(rowIndex, columns);
            }
        }
        logger.error("Antetul nu a fost gasit in primele " + (maxRow + 1) + " randuri.", null);
        throw new IllegalStateException("Header row not found. Expected headers: " + HEADER_HS_CODE + ", "
                + HEADER_KG + ", " + HEADER_VALUE + ", " + HEADER_PARCELS);
    }

    private Map<String, Integer> resolveColumns(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : row) {
            String value = formatter.formatCellValue(cell, evaluator);
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            columns.putIfAbsent(trimmed, cell.getColumnIndex());
        }
        return columns;
    }

    private String cellText(Row row, Integer columnIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (columnIndex == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell, evaluator);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isHsCodeTotal(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("grand total")) {
            return false;
        }
        return lower.contains("total");
    }

    private String extractHsCode(String value) {
        Matcher matcher = LEADING_DIGITS.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String safeNumericText(String value) {
        return value == null ? "" : value.replace(",", "").trim();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private static class HeaderInfo {
        private final int rowIndex;
        private final Map<String, Integer> columns;

        private HeaderInfo(int rowIndex, Map<String, Integer> columns) {
            this.rowIndex = rowIndex;
            this.columns = columns;
        }
    }
}

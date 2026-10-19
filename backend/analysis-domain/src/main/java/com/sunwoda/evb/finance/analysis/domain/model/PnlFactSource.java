package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * Immutable source location for one imported P&amp;L fact row.
 *
 * <p>This contract carries provenance only. It deliberately contains no
 * monetary values and therefore does not participate in amount calculation.</p>
 */
public final class PnlFactSource {
    private final String taskNo;
    private final String fileName;
    private final String checksum;
    private final String sheetName;
    private final int rowNo;
    private final String scopeCode;
    private final String baseCode;
    private final String productCode;
    private final String lineCode;

    public PnlFactSource(String taskNo, String fileName, String checksum,
                         String sheetName, int rowNo, String scopeCode,
                         String baseCode, String productCode, String lineCode) {
        this.taskNo = required(taskNo, "taskNo");
        this.fileName = required(fileName, "fileName");
        this.checksum = checksum;
        this.sheetName = required(sheetName, "sheetName");
        if (rowNo < 1) {
            throw new IllegalArgumentException("rowNo must be Excel 1-based and >= 1");
        }
        this.rowNo = rowNo;
        this.scopeCode = required(scopeCode, "scopeCode");
        this.baseCode = optionalDimension(baseCode);
        this.productCode = optionalDimension(productCode);
        this.lineCode = optionalDimension(lineCode);
    }

    public String getTaskNo() {
        return taskNo;
    }

    public String getFileName() {
        return fileName;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getSheetName() {
        return sheetName;
    }

    public int getRowNo() {
        return rowNo;
    }

    public String getScopeCode() {
        return scopeCode;
    }

    public String getBaseCode() {
        return baseCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getLineCode() {
        return lineCode;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalDimension(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

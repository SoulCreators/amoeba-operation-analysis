package com.sunwoda.evb.finance.analysis.application.model;

import java.util.Locale;

/** Supported result export formats and their transport metadata. */
public enum ExportFormat {
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    public static ExportFormat fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("format 不能为空");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (ExportFormat format : values()) {
            if (format.name().equals(normalized) || format.extension.equals(normalized)) {
                return format;
            }
        }
        throw new IllegalArgumentException("不支持的导出格式: " + code);
    }
}

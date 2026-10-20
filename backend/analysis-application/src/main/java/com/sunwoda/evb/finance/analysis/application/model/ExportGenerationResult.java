package com.sunwoda.evb.finance.analysis.application.model;

import java.util.Arrays;

/**
 * 导出渲染结果模型。
 *
 * <p>模型只承载渲染器产出的文件元数据和字节，不判断 CSV、XLSX、PDF 等具体格式，
 * 也不携带权限、保留期限或审计语义。</p>
 */
public final class ExportGenerationResult {
    private final String fileName;
    private final String contentType;
    private final byte[] content;

    /** 创建不可变的渲染结果，并复制文件字节避免外部修改。 */
    public ExportGenerationResult(String fileName, String contentType, byte[] content) {
        this.fileName = required(fileName, "fileName");
        this.contentType = required(contentType, "contentType");
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        this.content = Arrays.copyOf(content, content.length);
    }

    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }

    /** 返回内容副本，避免编排完成后结果被渲染器持有的数组修改。 */
    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    public long getContentLength() { return content.length; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}

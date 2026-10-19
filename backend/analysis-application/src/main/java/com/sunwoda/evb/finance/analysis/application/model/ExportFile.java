package com.sunwoda.evb.finance.analysis.application.model;

import java.util.Arrays;

/** 已授权下载的导出文件；构造和读取均使用内容副本。 */
public final class ExportFile {
    private final String fileName;
    private final String contentType;
    private final byte[] content;

    public ExportFile(String fileName, String contentType, byte[] content) {
        this.fileName = required(fileName, "fileName");
        this.contentType = required(contentType, "contentType");
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        this.content = Arrays.copyOf(content, content.length);
    }

    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getContentLength() { return content.length; }

    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}

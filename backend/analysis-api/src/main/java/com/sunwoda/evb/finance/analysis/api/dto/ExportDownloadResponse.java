package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;

/**
 * API download payload. The bytes are serialized by Jackson as base64 inside
 * the existing ApiResult envelope; no binary/file-format capability is implied.
 */
public class ExportDownloadResponse {
    private final String fileName;
    private final String contentType;
    private final long contentLength;
    private final byte[] content;

    public ExportDownloadResponse(ExportFile file) {
        this.fileName = file.getFileName();
        this.contentType = file.getContentType();
        this.contentLength = file.getContentLength();
        this.content = file.getContent();
    }

    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getContentLength() { return contentLength; }
    public byte[] getContent() { return content.clone(); }
}

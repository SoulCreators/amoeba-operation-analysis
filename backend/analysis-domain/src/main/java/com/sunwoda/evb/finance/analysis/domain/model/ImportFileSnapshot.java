package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Arrays;

/** Immutable copy of the previous import-file bytes and display name. */
public final class ImportFileSnapshot {
    private final String fileName;
    private final byte[] content;

    public ImportFileSnapshot(String fileName, byte[] content) {
        if (content == null) throw new IllegalArgumentException("文件内容不能为空");
        this.fileName = fileName == null ? "" : fileName;
        this.content = Arrays.copyOf(content, content.length);
    }

    public String getFileName() { return fileName; }

    public byte[] getContent() { return Arrays.copyOf(content, content.length); }
}

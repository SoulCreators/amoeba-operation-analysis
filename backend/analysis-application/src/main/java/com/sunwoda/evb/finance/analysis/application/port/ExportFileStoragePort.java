package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;

import java.util.Optional;

/**
 * 导出文件存储端口。
 *
 * <p>文件键和用户身份必须成对使用；实现不得只凭文件键返回内容。</p>
 */
public interface ExportFileStoragePort {
    void save(String fileKey, String userId, String fileName,
              String contentType, byte[] content);

    Optional<ExportFile> open(String fileKey, String userId);
}

package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;

import java.util.List;

/**
 * 源文件版本治理应用服务：长期保留、生效指针切换与发布后重算申请门禁。
 */
public interface SourceFileVersionService {
    /**
     * 登记一条新源文件版本（默认不生效，校验通过后再 {@link #activate(String)}）。
     *
     * <p>批次已发布或同对象出现重复文件哈希时，必须提供 {@code replaceReason}（重算申请/原因），
     * 否则拒绝；旧版本始终保留，不在此处删除。</p>
     */
    SourceFileVersion register(String batchNo, String datasetCode, String objectCode,
                               String templateCode, String templateVersion, String fileHash,
                               String objectKey, long rowCount, String replaceReason,
                               String uploadedBy);

    /** 切换生效指针：使同对象旧版本失效、指定版本生效（事务内完成）。 */
    SourceFileVersion activate(String fileId);

    /** 返回指定期间＋数据对象的版本历史，按版本号升序。 */
    List<SourceFileVersion> listVersions(String period, String objectCode);
}

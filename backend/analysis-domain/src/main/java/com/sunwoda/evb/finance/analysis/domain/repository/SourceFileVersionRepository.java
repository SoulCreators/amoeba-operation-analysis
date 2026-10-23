package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;

import java.util.List;
import java.util.Optional;

/**
 * 源文件版本持久化端口。
 *
 * <p>同一期间＋数据对象最多一个生效版本，由部分唯一索引在数据库侧兜底；生效指针切换
 * 由 {@link #switchActive(String, String, String)} 在事务内完成，旧行保留（长期保留）。</p>
 */
public interface SourceFileVersionRepository {
    /** 追加一条新版本（只插入，不删除旧版本）。返回携带持久化 id 的版本。 */
    SourceFileVersion save(SourceFileVersion version);

    Optional<SourceFileVersion> findByFileId(String fileId);

    /** 返回指定期间＋数据对象当前生效版本（至多一条）。 */
    Optional<SourceFileVersion> findActive(String period, String objectCode);

    /** 返回指定期间＋数据对象全部版本，按版本号升序（长期保留的历史）。 */
    List<SourceFileVersion> findByPeriodAndObject(String period, String objectCode);

    /** 当前最大版本号；对象尚未有版本时为 0。 */
    int nextVersionNo(String period, String objectCode);

    /** 指定期间＋数据对象是否已存在同一文件哈希的版本（重复文件需说明原因）。 */
    boolean existsByObjectAndHash(String period, String objectCode, String fileHash);

    /**
     * 切换生效指针：把该对象全部版本置为失效后，将指定版本置为生效。
     * 顺序为「先失效后生效」，避免违反「同对象最多一个生效版本」的部分唯一索引。
     */
    void switchActive(String period, String objectCode, String fileId);
}

package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/** FC版本治理元数据；不包含预测数值，数值由后续快照导入链路提供。 */
public class FcVersion {
    private final String versionCode;
    private final AnalysisPerspective perspective;
    private final String basePeriod;
    private final String coverageStart;
    private final String coverageEnd;
    private final String status;
    private final String uploadedBy;
    private final LocalDateTime uploadedAt;
    private final boolean official;
    private final String publishedBy;
    private final LocalDateTime publishedAt;

    public FcVersion(String versionCode, AnalysisPerspective perspective, String basePeriod,
                     String coverageStart, String coverageEnd, String status,
                     String uploadedBy, LocalDateTime uploadedAt, boolean official,
                     String publishedBy, LocalDateTime publishedAt) {
        this.versionCode = versionCode;
        this.perspective = perspective;
        this.basePeriod = basePeriod;
        this.coverageStart = coverageStart;
        this.coverageEnd = coverageEnd;
        this.status = status;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.official = official;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public String getVersionCode() { return versionCode; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public String getBasePeriod() { return basePeriod; }
    public String getCoverageStart() { return coverageStart; }
    public String getCoverageEnd() { return coverageEnd; }
    public String getStatus() { return status; }
    public String getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public boolean isOfficial() { return official; }
    public String getPublishedBy() { return publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}

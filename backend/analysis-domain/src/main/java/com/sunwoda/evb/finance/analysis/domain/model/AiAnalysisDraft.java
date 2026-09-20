package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AiAnalysisDraft {
    private final String batchNo;
    private final String versionNo;
    private final Map<String, String> sections;
    private final String generatedBy;
    private final LocalDateTime generatedAt;
    private final AiAnalysisDraftStatus status;
    private final String publishedBy;
    private final LocalDateTime publishedAt;

    public AiAnalysisDraft(String batchNo, String versionNo, Map<String, String> sections,
                           String generatedBy, LocalDateTime generatedAt,
                           AiAnalysisDraftStatus status, String publishedBy,
                           LocalDateTime publishedAt) {
        this.batchNo = batchNo;
        this.versionNo = versionNo;
        this.sections = Collections.unmodifiableMap(new LinkedHashMap<String, String>(sections));
        this.generatedBy = generatedBy;
        this.generatedAt = generatedAt;
        this.status = status;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public static AiAnalysisDraft draft(String batchNo, String versionNo,
                                        Map<String, String> sections, String generatedBy) {
        return new AiAnalysisDraft(batchNo, versionNo, sections, generatedBy, LocalDateTime.now(),
                AiAnalysisDraftStatus.DRAFT, null, null);
    }

    public AiAnalysisDraft publish(String operator) {
        if (status != AiAnalysisDraftStatus.DRAFT) {
            throw new IllegalStateException("只有AI草稿可以发布: " + status);
        }
        return new AiAnalysisDraft(batchNo, versionNo, sections, generatedBy, generatedAt,
                AiAnalysisDraftStatus.PUBLISHED, operator, LocalDateTime.now());
    }

    public String getBatchNo() { return batchNo; }
    public String getVersionNo() { return versionNo; }
    public Map<String, String> getSections() { return sections; }
    public String getGeneratedBy() { return generatedBy; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public AiAnalysisDraftStatus getStatus() { return status; }
    public String getPublishedBy() { return publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}

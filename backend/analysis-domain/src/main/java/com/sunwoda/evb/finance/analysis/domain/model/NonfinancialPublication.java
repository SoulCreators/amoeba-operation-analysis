package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 非财经指标（电芯优率）发布记录，按期间唯一。
 *
 * <p>发布门禁：仅当该期间无多巴长冲突（{@code conflictCount == 0}）时方可发布；
 * 冲突数在发布时从当前结果快照重算，不信任历史值。</p>
 */
public class NonfinancialPublication {
    private final String period;
    private final NonfinancialPublicationStatus status;
    private final int conflictCount;
    private final String publishedBy;
    private final LocalDateTime publishedAt;

    public NonfinancialPublication(String period, NonfinancialPublicationStatus status,
                                   int conflictCount, String publishedBy,
                                   LocalDateTime publishedAt) {
        if (status == null) throw new IllegalArgumentException("发布状态不能为空");
        if (conflictCount < 0) throw new IllegalArgumentException("冲突数不能为负数");
        this.period = requireText(period, "period");
        this.status = status;
        this.conflictCount = conflictCount;
        this.publishedBy = status == NonfinancialPublicationStatus.PUBLISHED
                ? requireText(publishedBy, "publishedBy") : publishedBy;
        this.publishedAt = status == NonfinancialPublicationStatus.PUBLISHED
                ? require(publishedAt, "publishedAt") : publishedAt;
    }

    /** 该期间尚未发布的草稿态。 */
    public static NonfinancialPublication draft(String period) {
        return new NonfinancialPublication(period, NonfinancialPublicationStatus.DRAFT, 0, null, null);
    }

    /** 由草稿进入已发布；调用方须先确认 {@code conflictCount == 0}。 */
    public NonfinancialPublication publish(int conflictCount, String operator, LocalDateTime at) {
        return new NonfinancialPublication(period, NonfinancialPublicationStatus.PUBLISHED,
                conflictCount, operator, at);
    }

    public String getPeriod() { return period; }
    public NonfinancialPublicationStatus getStatus() { return status; }
    public int getConflictCount() { return conflictCount; }
    public String getPublishedBy() { return publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }

    public boolean isPublished() { return status == NonfinancialPublicationStatus.PUBLISHED; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private static LocalDateTime require(LocalDateTime value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " 不能为空");
        return value;
    }
}

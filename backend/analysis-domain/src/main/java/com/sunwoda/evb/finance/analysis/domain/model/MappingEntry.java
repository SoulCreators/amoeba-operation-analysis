package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

public class MappingEntry {
    private final Long id;
    private final MappingType type;
    private final String matchKey;
    private String mappedValue;
    private boolean enabled;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public MappingEntry(Long id, MappingType type, String matchKey, String mappedValue,
                        boolean enabled, String updatedBy, LocalDateTime updatedAt) {
        this.id = id;
        this.type = type;
        this.matchKey = matchKey;
        this.mappedValue = mappedValue;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void update(String mappedValue, boolean enabled, String updatedBy) {
        if (mappedValue == null || mappedValue.trim().isEmpty()) {
            throw new IllegalArgumentException("映射值不能为空");
        }
        if (updatedBy == null || updatedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("更新人不能为空");
        }
        this.mappedValue = mappedValue;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public MappingType getType() { return type; }
    public String getMatchKey() { return matchKey; }
    public String getMappedValue() { return mappedValue; }
    public boolean isEnabled() { return enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

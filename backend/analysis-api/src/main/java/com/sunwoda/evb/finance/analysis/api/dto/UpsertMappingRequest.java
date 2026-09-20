package com.sunwoda.evb.finance.analysis.api.dto;

public class UpsertMappingRequest {
    private String type;
    private String matchKey;
    private String mappedValue;
    private boolean enabled = true;
    private String updatedBy;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMatchKey() { return matchKey; }
    public void setMatchKey(String matchKey) { this.matchKey = matchKey; }
    public String getMappedValue() { return mappedValue; }
    public void setMappedValue(String mappedValue) { this.mappedValue = mappedValue; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}

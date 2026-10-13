package com.sunwoda.evb.finance.analysis.api.dto;

public class CreateFcVersionRequest {
    private String versionCode;
    private String perspective;
    private String basePeriod;
    private String coverageStart;
    private String coverageEnd;

    public String getVersionCode() { return versionCode; }
    public void setVersionCode(String versionCode) { this.versionCode = versionCode; }
    public String getPerspective() { return perspective; }
    public void setPerspective(String perspective) { this.perspective = perspective; }
    public String getBasePeriod() { return basePeriod; }
    public void setBasePeriod(String basePeriod) { this.basePeriod = basePeriod; }
    public String getCoverageStart() { return coverageStart; }
    public void setCoverageStart(String coverageStart) { this.coverageStart = coverageStart; }
    public String getCoverageEnd() { return coverageEnd; }
    public void setCoverageEnd(String coverageEnd) { this.coverageEnd = coverageEnd; }
}

package com.sunwoda.evb.finance.analysis.api.dto;

public class CreateConfigurationImportRequest {
    private String type;
    private String fileName;
    private String checksum;
    private String createdBy;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}

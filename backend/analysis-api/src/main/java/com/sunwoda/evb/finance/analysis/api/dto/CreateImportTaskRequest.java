package com.sunwoda.evb.finance.analysis.api.dto;

public class CreateImportTaskRequest {
    private String datasetCode;
    private String fileName;
    private String checksum;
    private String createdBy;

    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}

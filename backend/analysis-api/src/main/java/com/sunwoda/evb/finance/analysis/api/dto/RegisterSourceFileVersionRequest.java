package com.sunwoda.evb.finance.analysis.api.dto;

/**
 * 登记源文件版本的请求载荷。
 *
 * <p>{@code replaceReason} 用于发布后重传的重算申请或重复文件的说明原因；缺省时若批次
 * 已发布或文件哈希重复将被服务端拒绝。</p>
 */
public class RegisterSourceFileVersionRequest {
    private String datasetCode;
    private String objectCode;
    private String templateCode;
    private String templateVersion;
    private String fileHash;
    private String objectKey;
    private long rowCount;
    private String replaceReason;

    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }
    public String getObjectCode() { return objectCode; }
    public void setObjectCode(String objectCode) { this.objectCode = objectCode; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public long getRowCount() { return rowCount; }
    public void setRowCount(long rowCount) { this.rowCount = rowCount; }
    public String getReplaceReason() { return replaceReason; }
    public void setReplaceReason(String replaceReason) { this.replaceReason = replaceReason; }
}

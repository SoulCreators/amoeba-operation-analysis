package com.sunwoda.evb.finance.analysis.api.dto;

/**
 * 历史计算版本人工修订 HTTP 请求。
 *
 * <p>本次修改：显式承载来源版本、修订原因和请求幂等键；操作者不从请求体读取，
 * 由 Controller 使用认证 Principal 传入应用服务。</p>
 */
public class ManualCalculationRevisionRequest {
    private String sourceVersionId;
    private String revisionReason;
    private String requestKey;

    public String getSourceVersionId() {
        return sourceVersionId;
    }

    public void setSourceVersionId(String sourceVersionId) {
        this.sourceVersionId = sourceVersionId;
    }

    public String getRevisionReason() {
        return revisionReason;
    }

    public void setRevisionReason(String revisionReason) {
        this.revisionReason = revisionReason;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }
}

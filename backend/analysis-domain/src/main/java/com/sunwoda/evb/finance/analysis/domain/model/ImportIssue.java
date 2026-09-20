package com.sunwoda.evb.finance.analysis.domain.model;

public class ImportIssue {
    private final Integer rowNo;
    private final String fieldName;
    private final String issueCode;
    private final String issueMessage;
    private final String rawValue;

    public ImportIssue(Integer rowNo, String fieldName, String issueCode,
                       String issueMessage, String rawValue) {
        this.rowNo = rowNo;
        this.fieldName = fieldName;
        this.issueCode = issueCode;
        this.issueMessage = issueMessage;
        this.rawValue = rawValue;
    }

    public Integer getRowNo() { return rowNo; }
    public String getFieldName() { return fieldName; }
    public String getIssueCode() { return issueCode; }
    public String getIssueMessage() { return issueMessage; }
    public String getRawValue() { return rawValue; }
}

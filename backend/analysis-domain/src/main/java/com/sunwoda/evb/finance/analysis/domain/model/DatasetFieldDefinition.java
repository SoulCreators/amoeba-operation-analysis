package com.sunwoda.evb.finance.analysis.domain.model;

public class DatasetFieldDefinition {
    private final String fieldCode;
    private final String fieldName;
    private final String dataType;
    private final boolean required;

    public DatasetFieldDefinition(String fieldCode, String fieldName,
                                  String dataType, boolean required) {
        this.fieldCode = fieldCode;
        this.fieldName = fieldName;
        this.dataType = dataType;
        this.required = required;
    }

    public String getFieldCode() { return fieldCode; }
    public String getFieldName() { return fieldName; }
    public String getDataType() { return dataType; }
    public boolean isRequired() { return required; }
}

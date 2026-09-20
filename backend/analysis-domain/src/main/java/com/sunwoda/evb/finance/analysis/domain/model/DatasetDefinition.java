package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.List;

public class DatasetDefinition {
    private final String code;
    private final String name;
    private final String category;
    private final boolean required;
    private final List<AnalysisPerspective> perspectives;
    private final List<DatasetFieldDefinition> fields;

    public DatasetDefinition(String code, String name, String category,
                             boolean required, List<AnalysisPerspective> perspectives) {
        this(code, name, category, required, perspectives, Collections.<DatasetFieldDefinition>emptyList());
    }

    public DatasetDefinition(String code, String name, String category,
                             boolean required, List<AnalysisPerspective> perspectives,
                             List<DatasetFieldDefinition> fields) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.required = required;
        this.perspectives = Collections.unmodifiableList(perspectives);
        this.fields = Collections.unmodifiableList(fields);
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public boolean isRequired() { return required; }
    public List<AnalysisPerspective> getPerspectives() { return perspectives; }
    public List<DatasetFieldDefinition> getFields() { return fields; }

    public boolean supports(AnalysisPerspective perspective) {
        return perspectives.contains(perspective);
    }
}

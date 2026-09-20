package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;

import java.io.InputStream;

public interface TemplateValidator {
    ImportValidationResult validate(String taskNo, DatasetDefinition definition, InputStream inputStream);
}

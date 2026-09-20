package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;

import java.io.InputStream;

/** Structural validator for configuration workbooks. Business application is a later step. */
public interface ConfigurationTemplateValidator {
    ImportValidationResult validate(String taskNo, ConfigurationImportType type, InputStream inputStream);
}

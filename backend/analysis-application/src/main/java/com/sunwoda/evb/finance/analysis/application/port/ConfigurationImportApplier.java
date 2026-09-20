package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;

import java.io.InputStream;

/** Applies only validated, supported configuration types to configuration repositories. */
public interface ConfigurationImportApplier {
    ConfigurationApplyResult apply(ConfigurationImportTask task, InputStream inputStream);
}

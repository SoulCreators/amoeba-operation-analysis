package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;

/** Receives operational alerts when an expired export file cannot be deleted. */
public interface ExportCleanupAlertPort {
    void alert(ExportCleanupTask task, String errorMessage);
}

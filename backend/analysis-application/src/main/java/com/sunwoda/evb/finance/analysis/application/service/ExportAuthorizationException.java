package com.sunwoda.evb.finance.analysis.application.service;

/** Raised when the export permission policy rejects a submission. */
public final class ExportAuthorizationException extends IllegalStateException {
    public ExportAuthorizationException(String reason) {
        super("导出权限拒绝: " + (reason == null || reason.trim().isEmpty()
                ? "unknown reason" : reason));
    }
}

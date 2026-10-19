package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportRequest;

import java.util.Optional;

/** Application use cases for creating, inspecting and downloading exports. */
public interface ExportService {
    ExportJob create(String authenticatedUserId, ExportRequest request);

    /** Explicit alias for callers using asynchronous-submission terminology. */
    default ExportJob submit(String authenticatedUserId, ExportRequest request) {
        return create(authenticatedUserId, request);
    }

    Optional<ExportJob> status(String authenticatedUserId, String jobId);

    Optional<ExportFile> download(String authenticatedUserId, String jobId);
}

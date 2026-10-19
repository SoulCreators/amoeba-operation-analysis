package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.service.ExportPermission;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;

/**
 * Minimal application port for asynchronous result export submission.
 *
 * <p>The caller must resolve {@link ExportPermission} first. Implementations
 * may persist, queue and render the task later, but must not broaden the
 * supplied summary/detail scopes or use a client-provided identity as an
 * authorization substitute.</p>
 */
public interface ExportTask {
    String submit(String batchNo, String resultVersion, AnalysisPerspective perspective,
                  String userId, ExportPermission permission);
}

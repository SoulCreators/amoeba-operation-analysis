package com.sunwoda.evb.finance.analysis.application.adapter.db;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Explicit db-profile boundary until a production ExportTaskPort exists.
 *
 * <p>The memory adapter is excluded by {@code !db}; this adapter makes the
 * missing production wiring visible at the use-case boundary instead of
 * allowing an accidental in-memory fallback.</p>
 */
@Repository
@Profile("db")
// 本次修复：移除 final 以允许 Spring AOP 在 db Profile 下创建接口适配代理，仍保持调用时失败关闭边界。
public class DbExportTaskUnavailableAdapter implements ExportTaskPort {
    private static final String MESSAGE =
            "db profile enabled but no production ExportTaskPort implementation is configured";

    @Override
    public ExportJob create(String userId, String requestKey,
                            String fileName, String contentType) {
        throw unavailable();
    }

    @Override
    public Optional<ExportJob> status(String jobId, String userId) {
        throw unavailable();
    }

    @Override
    public Optional<ExportFile> download(String jobId, String userId) {
        throw unavailable();
    }

    @Override
    public void markRunning(String jobId) {
        throw unavailable();
    }

    @Override
    public void complete(String jobId, String fileName, String contentType, byte[] content) {
        throw unavailable();
    }

    @Override
    public void fail(String jobId, String errorMessage) {
        throw unavailable();
    }

    private IllegalStateException unavailable() {
        return new IllegalStateException(MESSAGE);
    }
}

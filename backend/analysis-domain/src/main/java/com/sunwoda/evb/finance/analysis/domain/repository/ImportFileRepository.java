package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.ImportFileSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface ImportFileRepository {
    void save(String taskNo, String fileName, byte[] content);
    InputStream open(String taskNo) throws IOException;

    /**
     * Deletes the file associated with an import task when a later validation
     * step fails. The default implementation keeps existing adapters source
     * compatible; adapters that own persisted files should override it.
     */
    default void delete(String taskNo) {
        // Compatibility no-op for adapters that do not persist files locally.
    }

    default Optional<ImportFileSnapshot> snapshot(String taskNo) {
        return Optional.empty();
    }

    default void restore(String taskNo, ImportFileSnapshot snapshot) {
        if (snapshot == null) delete(taskNo);
        else save(taskNo, snapshot.getFileName(), snapshot.getContent());
    }
}

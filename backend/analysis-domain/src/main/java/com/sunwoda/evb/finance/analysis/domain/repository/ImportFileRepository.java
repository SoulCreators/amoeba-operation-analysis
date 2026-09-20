package com.sunwoda.evb.finance.analysis.domain.repository;

import java.io.IOException;
import java.io.InputStream;

public interface ImportFileRepository {
    void save(String taskNo, String fileName, byte[] content);
    InputStream open(String taskNo) throws IOException;
}

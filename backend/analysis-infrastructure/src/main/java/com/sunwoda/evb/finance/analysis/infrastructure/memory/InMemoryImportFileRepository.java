package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@Profile("!filesystem")
public class InMemoryImportFileRepository implements ImportFileRepository {
    private final ConcurrentMap<String, byte[]> files = new ConcurrentHashMap<String, byte[]>();

    @Override
    public void save(String taskNo, String fileName, byte[] content) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("文件内容不能为空");
        files.put(taskNo, Arrays.copyOf(content, content.length));
    }

    @Override
    public InputStream open(String taskNo) throws IOException {
        byte[] content = files.get(taskNo);
        if (content == null) throw new IOException("导入文件内容不存在: " + taskNo);
        return new ByteArrayInputStream(Arrays.copyOf(content, content.length));
    }
}

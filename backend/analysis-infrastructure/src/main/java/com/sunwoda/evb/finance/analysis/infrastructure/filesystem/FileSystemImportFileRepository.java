package com.sunwoda.evb.finance.analysis.infrastructure.filesystem;

import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 受控目录文件适配器。生产环境可由对象存储适配器替换；当前实现用于内网部署和UAT。
 */
@Repository
@Profile("filesystem")
public class FileSystemImportFileRepository implements ImportFileRepository {
    private final Path root;

    public FileSystemImportFileRepository(
            @Value("${analysis.storage.root:./data/imports}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public void save(String taskNo, String fileName, byte[] content) {
        validateTaskNo(taskNo);
        if (content == null || content.length == 0) throw new IllegalArgumentException("文件内容不能为空");
        try {
            Files.createDirectories(root);
            Files.write(filePath(taskNo), content);
            Files.write(namePath(taskNo), (fileName == null ? "" : fileName).getBytes("UTF-8"));
        } catch (IOException ex) {
            throw new IllegalStateException("导入文件保存失败: " + taskNo, ex);
        }
    }

    @Override
    public InputStream open(String taskNo) throws IOException {
        validateTaskNo(taskNo);
        Path file = filePath(taskNo);
        if (!Files.exists(file)) throw new IOException("导入文件内容不存在: " + taskNo);
        return Files.newInputStream(file);
    }

    private Path filePath(String taskNo) { return root.resolve(taskNo + ".bin"); }
    private Path namePath(String taskNo) { return root.resolve(taskNo + ".name"); }

    private void validateTaskNo(String taskNo) {
        if (taskNo == null || !taskNo.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("非法导入任务号");
        }
    }
}

package com.sunwoda.evb.finance.analysis.infrastructure.filesystem;

import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** 将导入问题以UTF-8、Base64字段格式落在受控目录，避免问题记录随进程重启丢失。 */
@Repository
@Profile("filesystem")
public class FileSystemImportIssueRepository implements ImportIssueRepository {
    private final Path root;

    public FileSystemImportIssueRepository(
            @Value("${analysis.storage.root:./data/imports}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public void replace(String taskNo, List<ImportIssue> issues) {
        validateTaskNo(taskNo);
        try {
            Files.createDirectories(root);
            List<String> lines = new ArrayList<String>();
            for (ImportIssue issue : issues == null ? Collections.<ImportIssue>emptyList() : issues) {
                lines.add(encode(issue.getRowNo() == null ? "" : String.valueOf(issue.getRowNo())) + "\t"
                        + encode(issue.getFieldName()) + "\t"
                        + encode(issue.getIssueCode()) + "\t"
                        + encode(issue.getIssueMessage()) + "\t"
                        + encode(issue.getRawValue()));
            }
            Files.write(issuePath(taskNo), lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("导入问题保存失败: " + taskNo, ex);
        }
    }

    @Override
    public List<ImportIssue> findByTaskNo(String taskNo) {
        validateTaskNo(taskNo);
        Path file = issuePath(taskNo);
        if (!Files.exists(file)) return Collections.emptyList();
        try {
            List<ImportIssue> result = new ArrayList<ImportIssue>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 5) continue;
                String row = decode(fields[0]);
                result.add(new ImportIssue(row.isEmpty() ? null : Integer.valueOf(row),
                        decode(fields[1]), decode(fields[2]), decode(fields[3]), decode(fields[4])));
            }
            return Collections.unmodifiableList(result);
        } catch (IOException ex) {
            throw new IllegalStateException("导入问题读取失败: " + taskNo, ex);
        }
    }

    private Path issuePath(String taskNo) { return root.resolve(taskNo + ".issues"); }
    private String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value)
                .getBytes(StandardCharsets.UTF_8));
    }
    private String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
    private void validateTaskNo(String taskNo) {
        if (taskNo == null || !taskNo.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("非法导入任务号");
        }
    }
}

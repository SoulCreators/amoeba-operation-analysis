package com.sunwoda.evb.finance.analysis.infrastructure.filesystem;

import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileSystemImportRepositoryTest {
    @Test
    void persistsImportFileAndIssuesAcrossRepositoryInstances() throws Exception {
        Path root = Files.createTempDirectory("amoeba-import-");
        FileSystemImportFileRepository files = new FileSystemImportFileRepository(root.toString());
        byte[] content = "sample".getBytes(StandardCharsets.UTF_8);
        files.save("TASK-001", "sample.xlsx", content);
        FileSystemImportFileRepository reopenedFiles = new FileSystemImportFileRepository(root.toString());
        assertArrayEquals(content, read(reopenedFiles.open("TASK-001")));

        FileSystemImportIssueRepository issues = new FileSystemImportIssueRepository(root.toString());
        issues.replace("TASK-001", Arrays.asList(new ImportIssue(3, "基地", "UNMATCHED",
                "无法匹配基地", "南昌\t基地")));
        FileSystemImportIssueRepository reopenedIssues = new FileSystemImportIssueRepository(root.toString());
        List<ImportIssue> loaded = reopenedIssues.findByTaskNo("TASK-001");
        assertEquals(1, loaded.size());
        assertEquals("南昌\t基地", loaded.get(0).getRawValue());
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[128];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        input.close();
        return output.toByteArray();
    }
}

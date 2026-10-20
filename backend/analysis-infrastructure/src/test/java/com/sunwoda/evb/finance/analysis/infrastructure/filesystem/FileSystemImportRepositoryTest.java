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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void rejectsTaskNumbersThatCouldEscapeTheControlledRoot() throws Exception {
        Path root = Files.createTempDirectory("amoeba-import-security-");
        FileSystemImportFileRepository files = new FileSystemImportFileRepository(root.toString());
        FileSystemImportIssueRepository issues = new FileSystemImportIssueRepository(root.toString());

        assertThrows(IllegalArgumentException.class,
                () -> files.save("../outside", "result.xlsx", new byte[] {1}));
        assertThrows(IllegalArgumentException.class,
                () -> files.open("../../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> issues.replace("../outside", java.util.Collections.<ImportIssue>emptyList()));
        assertThrows(IllegalArgumentException.class,
                () -> issues.findByTaskNo("../../outside"));
    }

    @Test
    void snapshotsAndRestoresPreviousFileBeforeDeletingIt() throws Exception {
        Path root = Files.createTempDirectory("amoeba-import-restore-");
        FileSystemImportFileRepository files = new FileSystemImportFileRepository(root.toString());
        byte[] original = "old".getBytes(StandardCharsets.UTF_8);
        files.save("TASK-RESTORE", "old.xlsx", original);

        com.sunwoda.evb.finance.analysis.domain.model.ImportFileSnapshot snapshot =
                files.snapshot("TASK-RESTORE").orElseThrow(() ->
                        new AssertionError("expected file snapshot"));
        files.save("TASK-RESTORE", "new.xlsx", "new".getBytes(StandardCharsets.UTF_8));
        files.restore("TASK-RESTORE", snapshot);
        assertArrayEquals(original, read(files.open("TASK-RESTORE")));
        assertTrue(Files.exists(root.resolve("TASK-RESTORE.name")));

        files.restore("TASK-RESTORE", null);
        assertThrows(java.io.IOException.class, () -> files.open("TASK-RESTORE"));
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

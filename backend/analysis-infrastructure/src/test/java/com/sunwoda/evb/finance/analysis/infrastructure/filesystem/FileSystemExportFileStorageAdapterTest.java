package com.sunwoda.evb.finance.analysis.infrastructure.filesystem;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemExportFileStorageAdapterTest {
    @Test
    void isAnExplicitFilesystemProfileAdapter() {
        Profile profile = FileSystemExportFileStorageAdapter.class
                .getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"filesystem"}, profile.value());
        assertNotNull(FileSystemExportFileStorageAdapter.class
                .getAnnotation(Repository.class));
    }

    @Test
    void requiresConfiguredRootAndUsesIt() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new FileSystemExportFileStorageAdapter("  "));

        Path configuredRoot = Files.createTempDirectory("amoeba-export-");
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(configuredRoot.toString());
        storage.save("job-001", "user-a", "result.csv", "text/csv",
                bytes("csv"));

        try (java.util.stream.Stream<Path> paths = Files.walk(configuredRoot)) {
            assertTrue(paths.anyMatch(path -> path.toString().endsWith(".export")));
        }
        assertFalse(Files.exists(configuredRoot.resolveSibling(
                configuredRoot.getFileName().toString() + ".export")));
    }

    @Test
    void rejectsFileKeyTraversalAndAbsolutePaths() throws Exception {
        Path root = Files.createTempDirectory("amoeba-export-");
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(root.toString());

        assertThrows(IllegalArgumentException.class,
                () -> storage.save("../outside", "user-a", "a.txt",
                        "text/plain", bytes("x")));
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(root.resolve("outside").toString(), "user-a",
                        "a.txt", "text/plain", bytes("x")));
    }

    @Test
    void bindsSaveAndOpenToUserAndCopiesBytes() throws Exception {
        Path root = Files.createTempDirectory("amoeba-export-");
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(root.toString());
        byte[] source = bytes("original");

        storage.save("job-001", "user-a", "result.csv", "text/csv", source);
        source[0] = 'X';

        Optional<ExportFile> wrongUser = storage.open("job-001", "user-b");
        assertFalse(wrongUser.isPresent());
        ExportFile file = storage.open("job-001", "user-a").get();
        byte[] returned = file.getContent();
        returned[0] = 'X';
        assertArrayEquals(bytes("original"), storage.open("job-001", "user-a")
                .get().getContent());
        assertEquals("result.csv", file.getFileName());
        assertEquals("text/csv", file.getContentType());
    }

    @Test
    void missingStoredFileReturnsEmpty() throws Exception {
        Path root = Files.createTempDirectory("amoeba-export-");
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(root.toString());
        storage.save("job-001", "user-a", "result.csv", "text/csv", bytes("x"));
        Path stored;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            stored = paths.filter(path -> path.toString().endsWith(".export"))
                    .findFirst().get();
        }
        Files.delete(stored);

        assertFalse(storage.open("job-001", "user-a").isPresent());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

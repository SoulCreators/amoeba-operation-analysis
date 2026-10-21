package com.sunwoda.evb.finance.analysis.infrastructure.filesystem;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Modifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

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
    void remainsSubclassableForSpringCglibProxy() {
        // 防止再次把带 Spring 仓储切面的适配器声明为 final，导致 filesystem Profile 启动失败。
        assertFalse(Modifier.isFinal(FileSystemExportFileStorageAdapter.class
                .getModifiers()));
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

    @Test
    void deleteIsIdempotentAndBoundToTheUserNamespace() throws Exception {
        Path root = Files.createTempDirectory("amoeba-export-");
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(root.toString());
        storage.save("job-001", "user-a", "result.csv", "text/csv", bytes("x"));

        storage.delete("job-001", "user-b");
        assertTrue(storage.open("job-001", "user-a").isPresent());

        storage.delete("job-001", "user-a");
        storage.delete("job-001", "user-a");
        assertFalse(storage.open("job-001", "user-a").isPresent());
        storage.delete("missing", "user-a");
    }

    @Test
    void deleteRejectsSeparatorsAndTraversal() throws Exception {
        Path root = Files.createTempDirectory("amoeba-export-");
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(root.toString());

        assertThrows(IllegalArgumentException.class,
                () -> storage.delete("../outside", "user-a"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.delete("nested/file", "user-a"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.delete("nested\\file", "user-a"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.delete(root.resolve("outside").toString(), "user-a"));
    }

    @Test
    void deleteDoesNotRecursivelyDeleteDirectory() throws Exception {
        Path root = Files.createTempDirectory("amoeba-export-");
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(root.toString());
        storage.save("job-001", "user-a", "result.csv", "text/csv", bytes("x"));
        Path userRoot = findUserRoot(root);
        Path target = userRoot.resolve("job-001.export");
        Files.delete(target);
        Files.createDirectory(target);
        Files.write(target.resolve("nested.txt"), bytes("must remain"));

        assertThrows(IllegalStateException.class,
                () -> storage.delete("job-001", "user-a"));
        assertTrue(Files.exists(target));
        assertTrue(Files.exists(target.resolve("nested.txt")));
    }

    @Test
    void deleteRejectsUserDirectorySymlink() throws Exception {
        Path root = Files.createTempDirectory("amoeba-export-");
        Path external = Files.createTempDirectory("amoeba-export-external-");
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(root.toString());
        storage.save("job-001", "user-a", "result.csv", "text/csv", bytes("x"));
        Path userRoot = findUserRoot(root);
        Files.delete(userRoot.resolve("job-001.export"));
        Files.delete(userRoot);
        if (!createSymbolicLink(userRoot, external)) return;

        assertThrows(IllegalStateException.class,
                () -> storage.delete("job-001", "user-a"));
        assertTrue(Files.exists(external));
    }

    @Test
    void deleteRejectsFileSymlink() throws Exception {
        Path root = Files.createTempDirectory("amoeba-export-");
        Path external = Files.createTempFile("amoeba-export-external-", ".export");
        Files.write(external, bytes("must remain"));
        FileSystemExportFileStorageAdapter storage =
                new FileSystemExportFileStorageAdapter(root.toString());
        storage.save("job-001", "user-a", "result.csv", "text/csv", bytes("x"));
        Path target = findUserRoot(root).resolve("job-001.export");
        Files.delete(target);
        if (!createSymbolicLink(target, external)) return;

        assertThrows(IllegalStateException.class,
                () -> storage.delete("job-001", "user-a"));
        assertTrue(Files.exists(external));
        assertTrue(Files.isSymbolicLink(target));
    }

    private static Path findUserRoot(Path root) throws IOException {
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("user-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("用户目录不存在"));
        }
    }

    private static boolean createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException ex) {
            return false;
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

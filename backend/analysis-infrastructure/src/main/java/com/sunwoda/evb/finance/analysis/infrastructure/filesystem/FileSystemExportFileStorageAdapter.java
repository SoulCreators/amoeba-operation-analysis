package com.sunwoda.evb.finance.analysis.infrastructure.filesystem;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Explicitly opted-in local-disk implementation of {@link ExportFileStoragePort}.
 *
 * <p>This is a deployment-local adapter, not an S3/OSS substitute. It is only
 * registered with the {@code filesystem} profile and requires the configured
 * {@code analysis.storage.root} property.</p>
 *
 * <p>Each user gets an opaque directory below the configured root. The file
 * key is resolved below that directory and checked after normalization, so a
 * relative traversal or absolute path cannot escape the root or user scope.</p>
 */
@Repository
@Profile("filesystem")
public final class FileSystemExportFileStorageAdapter implements ExportFileStoragePort {
    private static final int MAGIC = 0x414D4546;
    private static final int FORMAT_VERSION = 1;
    private static final String FILE_SUFFIX = ".export";

    private final Path root;

    public FileSystemExportFileStorageAdapter(
            @Value("${analysis.storage.root}") String root) {
        if (root == null || root.trim().isEmpty()) {
            throw new IllegalArgumentException("analysis.storage.root 不能为空");
        }
        this.root = Paths.get(root.trim()).toAbsolutePath().normalize();
    }

    @Override
    public void save(String fileKey, String userId, String fileName,
                     String contentType, byte[] content) {
        String key = required(fileKey, "fileKey");
        String owner = required(userId, "userId");
        String normalizedName = required(fileName, "fileName");
        String normalizedType = isBlank(contentType)
                ? "application/octet-stream" : contentType.trim();
        ExportFile file = new ExportFile(normalizedName, normalizedType, content);

        Path target = resolveStoragePath(key, owner);
        Path temporary = null;
        try {
            ensureRootAndParent(target);
            rejectExistingSymlink(target);
            temporary = Files.createTempFile(target.getParent(),
                    target.getFileName().toString(), ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 DataOutputStream data = new DataOutputStream(
                         new BufferedOutputStream(output))) {
                data.writeInt(MAGIC);
                data.writeInt(FORMAT_VERSION);
                data.writeUTF(file.getFileName());
                data.writeUTF(file.getContentType());
                byte[] bytes = file.getContent();
                data.writeInt(bytes.length);
                data.write(bytes, 0, bytes.length);
            }
            moveIntoPlace(temporary, target);
            temporary = null;
        } catch (IOException ex) {
            throw new IllegalStateException("导出文件保存失败: " + key, ex);
        } finally {
            deleteTemporary(temporary);
        }
    }

    @Override
    public Optional<ExportFile> open(String fileKey, String userId) {
        if (isBlank(fileKey) || isBlank(userId)) return Optional.empty();
        String key = fileKey.trim();
        String owner = userId.trim();
        Path target = resolveStoragePath(key, owner);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            ensureExistingFileIsInRoot(target);
            rejectExistingSymlink(target);
            try (InputStream input = Files.newInputStream(target);
                 DataInputStream data = new DataInputStream(
                         new BufferedInputStream(input))) {
                if (data.readInt() != MAGIC) {
                    throw new IOException("导出文件格式无效");
                }
                if (data.readInt() != FORMAT_VERSION) {
                    throw new IOException("导出文件版本不受支持");
                }
                String fileName = data.readUTF();
                String contentType = data.readUTF();
                int length = data.readInt();
                if (length <= 0) throw new IOException("导出文件内容为空");
                byte[] content = new byte[length];
                data.readFully(content);
                return Optional.of(new ExportFile(fileName, contentType, content));
            }
        } catch (NoSuchFileException ex) {
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("导出文件读取失败: " + key, ex);
        }
    }

    private Path resolveStoragePath(String fileKey, String userId) {
        Path userRoot = root.resolve(userScope(userId)).normalize();
        if (fileKey.indexOf('/') >= 0 || fileKey.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("fileKey 必须是单一路径段");
        }
        Path keyPath = userRoot.resolve(fileKey).normalize();
        if (keyPath.equals(userRoot) || !keyPath.startsWith(userRoot)) {
            throw new IllegalArgumentException("fileKey 不能逃逸文件存储根目录或用户目录");
        }
        Path fileName = keyPath.getFileName();
        if (fileName == null || fileName.toString().isEmpty()) {
            throw new IllegalArgumentException("fileKey 不能为空");
        }
        Path target = userRoot.resolve(fileName.toString() + FILE_SUFFIX);
        if (!target.startsWith(root) || !target.startsWith(userRoot)) {
            throw new IllegalArgumentException("fileKey 不能逃逸文件存储根目录或用户目录");
        }
        return target;
    }

    private void ensureRootAndParent(Path target) throws IOException {
        Files.createDirectories(root);
        Path realRoot = root.toRealPath();
        rejectUserDirectorySymlink(target.getParent());
        Files.createDirectories(target.getParent());
        Path realParent = target.getParent().toRealPath();
        if (!realParent.startsWith(realRoot)) {
            throw new IOException("文件存储路径不在配置根目录或用户目录内");
        }
    }

    private void ensureExistingFileIsInRoot(Path target) throws IOException {
        Path realRoot = root.toRealPath();
        rejectUserDirectorySymlink(target.getParent());
        Path realParent = target.getParent().toRealPath();
        if (!realParent.startsWith(realRoot)) {
            throw new IOException("文件存储路径不在配置根目录或用户目录内");
        }
    }

    private void rejectUserDirectorySymlink(Path userRoot) throws IOException {
        if (Files.isSymbolicLink(userRoot)) {
            throw new IOException("不允许通过符号链接访问用户导出目录");
        }
    }

    private void rejectExistingSymlink(Path target) throws IOException {
        if (Files.isSymbolicLink(target)) {
            throw new IOException("不允许通过符号链接访问导出文件");
        }
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) return;
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // Preserve the original save failure; the temporary file is harmless
            // and remains below the configured root for operational cleanup.
        }
    }

    private static String userScope(String userId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(userId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return "user-" + hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 不支持 SHA-256", ex);
        }
    }

    private static String required(String value, String field) {
        if (isBlank(value)) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package com.sunwoda.evb.finance.analysis.application.adapter.memory;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 轻量内存适配器，供本地运行和后续 API/worker 接线使用。
 *
 * <p>任务元数据和文件内容分开保存；下载先按 jobId/userId 校验任务归属，之后才读取文件。
 * 该实现不承诺跨进程持久化，生产环境可替换为数据库任务仓储和对象存储适配器。</p>
 */
@Repository
@Profile("!db")
public class InMemoryExportTaskAdapter implements ExportTaskPort, ExportFileStoragePort {
    private final ConcurrentMap<String, ExportJob> jobs =
            new ConcurrentHashMap<String, ExportJob>();
    private final ConcurrentMap<String, String> requestIndex =
            new ConcurrentHashMap<String, String>();
    private final ConcurrentMap<String, ExportTaskSnapshot> snapshots =
            new ConcurrentHashMap<String, ExportTaskSnapshot>();
    private final ConcurrentMap<String, StoredFile> files =
            new ConcurrentHashMap<String, StoredFile>();

    @Override
    public synchronized ExportJob create(String userId, String requestKey,
                                         String fileName, String contentType) {
        return createInternal(userId, requestKey, fileName, contentType, null);
    }

    @Override
    public synchronized ExportJob create(String userId, String requestKey,
                                         ExportTaskSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot 不能为空");
        return createInternal(userId, requestKey, snapshot.fileName(),
                snapshot.contentType(), snapshot);
    }

    @Override
    public synchronized Optional<ExportTaskSnapshot> snapshot(String jobId) {
        if (isBlank(jobId)) return Optional.empty();
        ExportTaskSnapshot snapshot = snapshots.get(jobId.trim());
        return snapshot == null ? Optional.<ExportTaskSnapshot>empty()
                : Optional.of(snapshot);
    }

    private ExportJob createInternal(String userId, String requestKey,
                                     String fileName, String contentType,
                                     ExportTaskSnapshot snapshot) {
        String owner = required(userId, "userId");
        String key = required(requestKey, "requestKey");
        String normalizedName = required(fileName, "fileName");
        String normalizedType = defaultContentType(contentType);
        String requestIndexKey = owner + "\u0000" + key;
        String existingJobId = requestIndex.get(requestIndexKey);
        if (existingJobId != null) {
            ExportJob existing = jobs.get(existingJobId);
            if (existing != null) return existing;
            requestIndex.remove(requestIndexKey, existingJobId);
        }
        String jobId = "EXP-" + UUID.randomUUID().toString().replace("-", "");
        ExportJob job = ExportJob.queued(jobId, owner, key, normalizedName,
                normalizedType, LocalDateTime.now());
        jobs.put(jobId, job);
        if (snapshot != null) {
            snapshots.put(jobId, snapshot);
        }
        requestIndex.put(requestIndexKey, jobId);
        return job;
    }

    @Override
    public synchronized Optional<ExportJob> status(String jobId, String userId) {
        ExportJob job = findOwnedJob(jobId, userId);
        if (job != null && job.isExpired(LocalDateTime.now())) {
            job = expire(job);
        }
        return job == null ? Optional.<ExportJob>empty() : Optional.of(job);
    }

    @Override
    public synchronized Optional<ExportFile> download(String jobId, String userId) {
        ExportJob job = findOwnedJob(jobId, userId);
        if (job != null && job.isExpired(LocalDateTime.now())) {
            job = expire(job);
        }
        if (job == null || job.getStatus() != ExportJobStatus.SUCCEEDED) {
            return Optional.empty();
        }
        // 只有归属校验成功且任务已完成，才访问文件存储。
        return open(job.getJobId(), userId);
    }

    @Override
    public synchronized void markRunning(String jobId) {
        ExportJob job = requireJob(jobId);
        if (job.getStatus() == ExportJobStatus.RUNNING) return;
        if (job.getStatus() != ExportJobStatus.QUEUED) {
            throw new IllegalStateException("导出任务不可开始: " + jobId);
        }
        if (job.isExpired(LocalDateTime.now())) {
            throw new IllegalStateException("导出任务已过期: " + jobId);
        }
        jobs.put(jobId, job.running(LocalDateTime.now()));
    }

    @Override
    public synchronized void complete(String jobId, String fileName,
                                      String contentType, byte[] content) {
        ExportJob job = requireJob(jobId);
        if (job.getStatus() != ExportJobStatus.QUEUED
                && job.getStatus() != ExportJobStatus.RUNNING) {
            throw new IllegalStateException("导出任务不可完成: " + jobId);
        }
        if (job.isExpired(LocalDateTime.now())) {
            throw new IllegalStateException("导出任务已过期: " + jobId);
        }
        String normalizedName = required(fileName, "fileName");
        String normalizedType = defaultContentType(contentType);
        save(jobId, job.getUserId(), normalizedName, normalizedType, content);
        jobs.put(jobId, job.succeeded(normalizedName, normalizedType, LocalDateTime.now()));
    }

    @Override
    public synchronized void fail(String jobId, String errorMessage) {
        ExportJob job = requireJob(jobId);
        if (job.getStatus() == ExportJobStatus.SUCCEEDED) {
            throw new IllegalStateException("已完成的导出任务不可失败: " + jobId);
        }
        if (job.getStatus() == ExportJobStatus.FAILED) return;
        jobs.put(jobId, job.failed(errorMessage, LocalDateTime.now()));
    }

    @Override
    public synchronized void save(String fileKey, String userId, String fileName,
                                   String contentType, byte[] content) {
        String key = required(fileKey, "fileKey");
        String owner = required(userId, "userId");
        String normalizedName = required(fileName, "fileName");
        String normalizedType = defaultContentType(contentType);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        files.put(key, new StoredFile(owner, new ExportFile(normalizedName,
                normalizedType, Arrays.copyOf(content, content.length))));
    }

    @Override
    public synchronized Optional<ExportFile> open(String fileKey, String userId) {
        if (isBlank(fileKey) || isBlank(userId)) return Optional.empty();
        StoredFile file = files.get(fileKey.trim());
        if (file == null || !file.ownerUserId.equals(userId.trim())) {
            return Optional.empty();
        }
        return Optional.of(file.file);
    }

    @Override
    public synchronized void delete(String fileKey, String userId) {
        if (isBlank(fileKey) || isBlank(userId)) return;
        String key = fileKey.trim();
        StoredFile file = files.get(key);
        if (file != null && file.ownerUserId.equals(userId.trim())) {
            files.remove(key, file);
        }
    }

    private ExportJob findOwnedJob(String jobId, String userId) {
        if (isBlank(jobId) || isBlank(userId)) return null;
        ExportJob job = jobs.get(jobId.trim());
        return job != null && job.getUserId().equals(userId.trim()) ? job : null;
    }

    private ExportJob requireJob(String jobId) {
        String key = required(jobId, "jobId");
        ExportJob job = jobs.get(key);
        if (job == null) throw new IllegalArgumentException("导出任务不存在: " + key);
        return job;
    }

    private ExportJob expire(ExportJob job) {
        ExportJob expired = job.expired(LocalDateTime.now());
        jobs.put(job.getJobId(), expired);
        return expired;
    }

    private static String defaultContentType(String contentType) {
        return isBlank(contentType) ? "application/octet-stream" : contentType.trim();
    }

    private static String required(String value, String field) {
        if (isBlank(value)) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class StoredFile {
        private final String ownerUserId;
        private final ExportFile file;

        private StoredFile(String ownerUserId, ExportFile file) {
            this.ownerUserId = ownerUserId;
            this.file = file;
        }
    }
}

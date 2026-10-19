package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 来源追溯内存实现。
 *
 * <p>replace 的校验、删除和重写在实例级互斥区内完成，保证同一实例内的替换不会
 * 被并发查询或其他替换观察到中间状态。该实现仅用于契约测试和本地替身，不代表
 * 事务原子性、跨进程一致性或生产 JDBC 实现。</p>
 */
@Repository
@Profile("!db")
public class InMemoryPnlFactSourceWriter implements PnlFactSourceWriter {
    private final ConcurrentMap<SourceKey, List<PnlFactSource>> store =
            new ConcurrentHashMap<SourceKey, List<PnlFactSource>>();

    @Override
    public synchronized void replace(AnalysisBatch batch, ImportTask task,
                                     List<PnlFactSource> sources) {
        if (batch == null) {
            throw new IllegalArgumentException("batch 不能为空");
        }
        if (task == null) {
            throw new IllegalArgumentException("task 不能为空");
        }
        if (sources == null) {
            throw new IllegalArgumentException("sources 不能为空");
        }
        if (!sameText(batch.getBatchNo(), task.getBatchNo())) {
            throw new IllegalArgumentException("task.batchNo 与 batch.batchNo 不一致");
        }

        Set<String> locations = new HashSet<String>();
        List<PnlFactSource> replacement = new ArrayList<PnlFactSource>(sources.size());
        for (PnlFactSource source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("sources 不得包含 null");
            }
            if (!sameText(source.getTaskNo(), task.getTaskNo())) {
                throw new IllegalArgumentException("source.taskNo 与 task.taskNo 不一致");
            }
            String location = source.getSheetName() + "::" + source.getRowNo();
            if (!locations.add(location)) {
                throw new IllegalArgumentException("同一 Sheet 的行号来源重复: " + location);
            }
            replacement.add(source);
        }

        SourceKey key = new SourceKey(batch.getBatchNo(), batch.getPerspective(), task.getTaskNo());
        if (replacement.isEmpty()) {
            store.remove(key);
        } else {
            store.put(key, Collections.unmodifiableList(replacement));
        }
    }

    @Override
    public synchronized List<PnlFactSource> findByBatchNoAndPerspectiveAndTaskNo(
            String batchNo, AnalysisPerspective perspective, String taskNo) {
        SourceKey key = new SourceKey(batchNo, perspective, taskNo);
        List<PnlFactSource> sources = store.get(key);
        if (sources == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<PnlFactSource>(sources));
    }

    private static boolean sameText(String left, String right) {
        return left != null && left.equals(right);
    }

    private static final class SourceKey {
        private final String batchNo;
        private final AnalysisPerspective perspective;
        private final String taskNo;

        private SourceKey(String batchNo, AnalysisPerspective perspective, String taskNo) {
            this.batchNo = requiredText(batchNo, "batchNo");
            this.perspective = requiredPerspective(perspective);
            this.taskNo = requiredText(taskNo, "taskNo");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SourceKey)) return false;
            SourceKey that = (SourceKey) other;
            return batchNo.equals(that.batchNo)
                    && perspective == that.perspective
                    && taskNo.equals(that.taskNo);
        }

        @Override
        public int hashCode() {
            int result = batchNo.hashCode();
            result = 31 * result + perspective.hashCode();
            result = 31 * result + taskNo.hashCode();
            return result;
        }

        private static String requiredText(String value, String fieldName) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(fieldName + " 不能为空");
            }
            return value;
        }

        private static AnalysisPerspective requiredPerspective(AnalysisPerspective value) {
            if (value == null) {
                throw new IllegalArgumentException("perspective 不能为空");
            }
            return value;
        }
    }
}

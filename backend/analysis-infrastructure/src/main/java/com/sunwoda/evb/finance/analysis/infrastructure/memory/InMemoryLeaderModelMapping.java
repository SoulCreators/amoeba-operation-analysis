package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.port.LeaderModelMapping;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 非财经巴长型号映射内存实现。
 *
 * <p>生产实现依赖通用销售项目映射（FR-CFG-001），此处以内存映射占位，默认无映射；
 * 通过 {@link #register(String, String)} 注入型号→巴长工号用于测试与接线。</p>
 */
@Repository
public class InMemoryLeaderModelMapping implements LeaderModelMapping {
    private final Map<String, Set<String>> leadersByModel = new LinkedHashMap<String, Set<String>>();

    /** 登记一个型号→巴长工号的归属（可多次调用模拟多巴长冲突）。 */
    public synchronized void register(String cellModel, String leaderEmployeeNo) {
        leadersByModel
                .computeIfAbsent(required(cellModel, "cellModel"), k -> new LinkedHashSet<String>())
                .add(required(leaderEmployeeNo, "leaderEmployeeNo"));
    }

    @Override
    public synchronized Set<String> leadersOf(String cellModel) {
        Set<String> leaders = leadersByModel.get(required(cellModel, "cellModel"));
        return leaders == null ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(leaders);
    }

    @Override
    public synchronized Set<String> modelsOf(String leaderEmployeeNo) {
        String required = required(leaderEmployeeNo, "leaderEmployeeNo");
        Set<String> models = new LinkedHashSet<String>();
        for (Map.Entry<String, Set<String>> entry : leadersByModel.entrySet()) {
            if (entry.getValue().contains(required)) {
                models.add(entry.getKey());
            }
        }
        return models;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}

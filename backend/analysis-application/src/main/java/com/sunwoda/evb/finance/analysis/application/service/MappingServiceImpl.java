package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MappingServiceImpl implements MappingService {
    private final MappingEntryRepository repository;

    public MappingServiceImpl(MappingEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public MappingEntry upsert(MappingType type, String matchKey, String mappedValue,
                               boolean enabled, String updatedBy) {
        if (type == null) throw new IllegalArgumentException("映射类型不能为空");
        require(matchKey, "matchKey");
        require(mappedValue, "mappedValue");
        require(updatedBy, "updatedBy");
        MappingEntry entry = repository.find(type, matchKey).orElse(null);
        if (entry == null) {
            entry = new MappingEntry(null, type, matchKey, mappedValue, enabled,
                    updatedBy, LocalDateTime.now());
        } else {
            entry.update(mappedValue, enabled, updatedBy);
        }
        return repository.save(entry);
    }

    @Override
    public List<MappingEntry> list(MappingType type, boolean includeDisabled) {
        if (type == null) throw new IllegalArgumentException("映射类型不能为空");
        return repository.list(type, includeDisabled);
    }

    @Override
    public MappingEntry disable(MappingType type, String matchKey, String updatedBy) {
        MappingEntry entry = repository.find(type, matchKey)
                .orElseThrow(() -> new IllegalArgumentException("映射不存在: " + matchKey));
        entry.update(entry.getMappedValue(), false, updatedBy);
        return repository.save(entry);
    }

    private void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }
}

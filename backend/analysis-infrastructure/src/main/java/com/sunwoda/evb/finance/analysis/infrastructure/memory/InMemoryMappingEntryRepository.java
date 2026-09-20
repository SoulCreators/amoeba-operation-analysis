package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryMappingEntryRepository implements MappingEntryRepository {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final ConcurrentMap<String, MappingEntry> store =
            new ConcurrentHashMap<String, MappingEntry>();

    @Override
    public MappingEntry save(MappingEntry entry) {
        if (entry.getId() == null) {
            MappingEntry saved = new MappingEntry(sequence.getAndIncrement(), entry.getType(),
                    entry.getMatchKey(), entry.getMappedValue(), entry.isEnabled(),
                    entry.getUpdatedBy(), entry.getUpdatedAt());
            store.put(key(entry.getType(), entry.getMatchKey()), saved);
            return saved;
        }
        store.put(key(entry.getType(), entry.getMatchKey()), entry);
        return entry;
    }

    @Override
    public Optional<MappingEntry> find(MappingType type, String matchKey) {
        return Optional.ofNullable(store.get(key(type, matchKey)));
    }

    @Override
    public List<MappingEntry> list(MappingType type, boolean includeDisabled) {
        List<MappingEntry> result = new ArrayList<MappingEntry>();
        for (MappingEntry entry : store.values()) {
            if (entry.getType() == type && (includeDisabled || entry.isEnabled())) result.add(entry);
        }
        return Collections.unmodifiableList(result);
    }

    private String key(MappingType type, String matchKey) {
        return type.name() + "::" + matchKey;
    }
}

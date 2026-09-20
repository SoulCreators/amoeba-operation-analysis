package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;

import java.util.List;
import java.util.Optional;

public interface MappingEntryRepository {
    MappingEntry save(MappingEntry entry);
    Optional<MappingEntry> find(MappingType type, String matchKey);
    List<MappingEntry> list(MappingType type, boolean includeDisabled);
}

package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;

import java.util.List;

public interface MappingService {
    MappingEntry upsert(MappingType type, String matchKey, String mappedValue,
                        boolean enabled, String updatedBy);
    List<MappingEntry> list(MappingType type, boolean includeDisabled);
    MappingEntry disable(MappingType type, String matchKey, String updatedBy);
}

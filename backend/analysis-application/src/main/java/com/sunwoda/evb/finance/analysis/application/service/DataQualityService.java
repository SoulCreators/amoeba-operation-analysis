package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.DataQualityReport;

public interface DataQualityService {
    DataQualityReport get(String batchNo);
}

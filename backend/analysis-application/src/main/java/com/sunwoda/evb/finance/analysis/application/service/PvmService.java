package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;

import java.util.List;

public interface PvmService {
    List<PvmResult> analyze(String batchNo, String userId);
}

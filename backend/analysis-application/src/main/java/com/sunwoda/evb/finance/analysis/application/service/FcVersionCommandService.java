package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;

import java.io.InputStream;

public interface FcVersionCommandService {
    FcVersion create(String versionCode, AnalysisPerspective perspective, String basePeriod,
                     String coverageStart, String coverageEnd, String operator);

    ImportValidationResult uploadSnapshots(String versionCode, AnalysisPerspective perspective,
                                           String basePeriod, InputStream inputStream, String operator);

    FcVersion publish(String versionCode, AnalysisPerspective perspective, String basePeriod,
                     String operator);
}

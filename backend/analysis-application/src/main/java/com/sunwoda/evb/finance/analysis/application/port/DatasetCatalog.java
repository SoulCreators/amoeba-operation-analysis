package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;

import java.util.List;

public interface DatasetCatalog {
    List<DatasetDefinition> list(AnalysisPerspective perspective);
    DatasetDefinition require(String datasetCode, AnalysisPerspective perspective);
}

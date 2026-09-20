package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Repository
public class InMemoryDatasetCatalog implements DatasetCatalog {
    private final List<DatasetDefinition> definitions = Arrays.asList(
            definition("BASE_ACT_INC_COST", "基地实际收入成本明细", true, AnalysisPerspective.BASE),
            definition("BASE_BUD_INC_COST", "基地年度预算收入成本", true, AnalysisPerspective.BASE),
            definition("BASE_IDLE", "基地闲置费用", true, AnalysisPerspective.BASE),
            definition("BASE_L3_EXP", "基地三级费用明细", true, AnalysisPerspective.BASE),
            definition("BASE_LAB_MFG_EXP", "实验室与制造体系费用", true, AnalysisPerspective.BASE),
            definition("BASE_LOGISTICS", "基地仓储物流费", false, AnalysisPerspective.BASE),
            definition("BASE_IMPAIRMENT", "基地减值结果", false, AnalysisPerspective.BASE),
            definition("BASE_BUD_SHARE", "基地预算占比", false, AnalysisPerspective.BASE),
            definition("PLBU_ACT_INC_COST", "产品线/事业部实际收入成本", true,
                    AnalysisPerspective.PRODUCT_LINE, AnalysisPerspective.BUSINESS_UNIT),
            definition("PLBU_BUD_INC_COST", "产品线/事业部年度预算收入成本", true,
                    AnalysisPerspective.PRODUCT_LINE, AnalysisPerspective.BUSINESS_UNIT),
            definition("PLBU_IDLE", "产品线/事业部闲置费用", false,
                    AnalysisPerspective.PRODUCT_LINE, AnalysisPerspective.BUSINESS_UNIT),
            definition("PLBU_L3_EXP", "产品线/事业部三级费用", true,
                    AnalysisPerspective.PRODUCT_LINE, AnalysisPerspective.BUSINESS_UNIT),
            definition("PLBU_RD_EXP", "产品线/事业部研发费用", false,
                    AnalysisPerspective.PRODUCT_LINE, AnalysisPerspective.BUSINESS_UNIT),
            definition("PLBU_IMPAIRMENT", "产品线/事业部减值结果", false,
                    AnalysisPerspective.PRODUCT_LINE, AnalysisPerspective.BUSINESS_UNIT)
    );

    @Override
    public List<DatasetDefinition> list(AnalysisPerspective perspective) {
        List<DatasetDefinition> result = new ArrayList<DatasetDefinition>();
        for (DatasetDefinition definition : definitions) {
            if (definition.supports(perspective)) result.add(definition);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public DatasetDefinition require(String datasetCode, AnalysisPerspective perspective) {
        for (DatasetDefinition definition : definitions) {
            if (definition.getCode().equalsIgnoreCase(datasetCode)
                    && definition.supports(perspective)) return definition;
        }
        throw new IllegalArgumentException("数据集不适用于当前视角: " + datasetCode + " / " + perspective.getCode());
    }

    private DatasetDefinition definition(String code, String name, boolean required,
                                         AnalysisPerspective... perspectives) {
        return new DatasetDefinition(code, name, "MONTHLY", required,
                Arrays.asList(perspectives));
    }
}

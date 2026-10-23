package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 财务样表是多工作表工作簿，数据表按表名定位而非固定取第一个工作表；
 * 无法匹配时回退到第一个工作表，保持既有标准模板导入行为不变。
 */
final class PoiSheetLocator {
    private PoiSheetLocator() {
    }

    static Sheet locate(Workbook workbook, String datasetCode) {
        Sheet located = findSheet(workbook, datasetCode);
        return located != null ? located : workbook.getSheetAt(0);
    }

    /** 返回首个匹配关键字的表；无匹配返回 null，不回退首表（供整本自动检测用）。 */
    static Sheet findSheet(Workbook workbook, String datasetCode) {
        for (String keyword : keywords(datasetCode)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String name = workbook.getSheetName(i);
                if (name != null && name.contains(keyword)) {
                    return workbook.getSheetAt(i);
                }
            }
        }
        return null;
    }

    /**
     * 整本自动检测仅纳入能被唯一表名稳定识别的数据集；
     * 研发按 2.11~2.14 汇总尚未接入、实验室/制造体系无稳定表名关键字，不自动拆分。
     */
    static boolean isAutoDetectable(String datasetCode) {
        if (datasetCode == null) return false;
        String code = datasetCode.toUpperCase(Locale.ROOT);
        if (code.contains("RD_EXP") || code.contains("LAB_MFG_EXP")) return false;
        return !keywords(datasetCode).isEmpty();
    }

    private static List<String> keywords(String datasetCode) {
        if (datasetCode == null) return Collections.emptyList();
        String code = datasetCode.toUpperCase(Locale.ROOT);
        if (code.contains("INC_COST")) {
            if (code.contains("_BUD_")) {
                return Arrays.asList("收入成本明细-预算", "收入成本明细", "预算");
            }
            return Arrays.asList("收入成本明细-实际", "收入成本明细", "实际");
        }
        if (code.contains("L3_EXP")) return Arrays.asList("三级费用明细", "期间费用", "费用明细");
        if (code.contains("RD_EXP")) return Arrays.asList("研发", "研发费用");
        if (code.contains("IDLE")) return Arrays.asList("闲置");
        if (code.contains("IMPAIRMENT")) return Arrays.asList("减值");
        return Collections.emptyList();
    }
}

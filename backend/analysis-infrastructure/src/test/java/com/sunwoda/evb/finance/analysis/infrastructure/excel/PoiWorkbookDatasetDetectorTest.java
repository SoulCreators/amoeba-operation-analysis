package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoiWorkbookDatasetDetectorTest {
    private final PoiWorkbookDatasetDetector detector = new PoiWorkbookDatasetDetector();

    @Test
    void detectsDatasetsBySheetNameAndExcludesUnsupportedSources() throws Exception {
        byte[] content = workbookWithSheets("导出摘要", "2.5收入成本明细-预算", "2.6收入成本明细-实际",
                "2.17三级费用明细表", "2.7闲置费用", "2.16资产减值损失", "1.11 研发费用BY项目");
        List<String> candidates = Arrays.asList("PLBU_ACT_INC_COST", "PLBU_BUD_INC_COST",
                "PLBU_L3_EXP", "PLBU_IDLE", "PLBU_IMPAIRMENT", "PLBU_RD_EXP");

        Set<String> present = detector.detectPresentDatasets(content, candidates);

        assertTrue(present.contains("PLBU_ACT_INC_COST"));
        assertTrue(present.contains("PLBU_BUD_INC_COST"));
        assertTrue(present.contains("PLBU_L3_EXP"));
        assertTrue(present.contains("PLBU_IDLE"));
        assertTrue(present.contains("PLBU_IMPAIRMENT"));
        assertFalse(present.contains("PLBU_RD_EXP"), "研发按 2.11~2.14 汇总未接，不应自动拆分");
    }

    @Test
    void returnsEmptyWhenNoSheetMatches() throws Exception {
        byte[] content = workbookWithSheets("导出摘要");

        Set<String> present = detector.detectPresentDatasets(content,
                Arrays.asList("PLBU_ACT_INC_COST", "PLBU_L3_EXP"));

        assertTrue(present.isEmpty());
    }

    @Test
    void rejectsCorruptedWorkbook() {
        assertThrows(IllegalArgumentException.class,
                () -> detector.detectPresentDatasets("not-excel".getBytes(StandardCharsets.UTF_8),
                        Arrays.asList("PLBU_ACT_INC_COST")));
    }

    private byte[] workbookWithSheets(String... names) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            for (String name : names) {
                workbook.createSheet(name);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } finally {
            workbook.close();
        }
    }
}

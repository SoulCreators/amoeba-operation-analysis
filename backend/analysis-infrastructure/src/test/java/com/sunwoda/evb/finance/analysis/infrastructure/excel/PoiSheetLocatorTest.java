package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoiSheetLocatorTest {
    @Test
    void findSheetMatchesByKeywordWithoutFallingBack() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            workbook.createSheet("导出摘要");
            workbook.createSheet("2.6收入成本明细-实际");

            Sheet found = PoiSheetLocator.findSheet(workbook, "PLBU_ACT_INC_COST");
            Sheet missing = PoiSheetLocator.findSheet(workbook, "PLBU_L3_EXP");

            assertEquals("2.6收入成本明细-实际", found.getSheetName());
            assertNull(missing);
        } finally {
            workbook.close();
        }
    }

    @Test
    void locateFallsBackToFirstSheetWhenNoKeywordMatches() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            workbook.createSheet("导出摘要");
            workbook.createSheet("2.6收入成本明细-实际");

            Sheet located = PoiSheetLocator.locate(workbook, "PLBU_L3_EXP");

            assertEquals("导出摘要", located.getSheetName());
        } finally {
            workbook.close();
        }
    }

    @Test
    void isAutoDetectableExcludesUnsupportedSources() {
        assertTrue(PoiSheetLocator.isAutoDetectable("PLBU_ACT_INC_COST"));
        assertTrue(PoiSheetLocator.isAutoDetectable("PLBU_L3_EXP"));
        assertFalse(PoiSheetLocator.isAutoDetectable("PLBU_RD_EXP"));
        assertFalse(PoiSheetLocator.isAutoDetectable("BASE_LAB_MFG_EXP"));
        assertFalse(PoiSheetLocator.isAutoDetectable("BASE_LOGISTICS"));
    }
}

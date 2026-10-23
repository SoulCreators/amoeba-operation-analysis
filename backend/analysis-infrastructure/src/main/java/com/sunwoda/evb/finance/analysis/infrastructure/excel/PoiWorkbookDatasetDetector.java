package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.WorkbookDatasetDetector;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class PoiWorkbookDatasetDetector implements WorkbookDatasetDetector {
    @Override
    public Set<String> detectPresentDatasets(byte[] content, List<String> candidateCodes) {
        Set<String> present = new LinkedHashSet<String>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            for (String code : candidateCodes) {
                if (PoiSheetLocator.isAutoDetectable(code)
                        && PoiSheetLocator.findSheet(workbook, code) != null) {
                    present.add(code);
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("工作簿无法解析: " + ex.getMessage(), ex);
        }
        return present;
    }
}

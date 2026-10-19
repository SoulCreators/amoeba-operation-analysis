package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads row-level Excel provenance beside the existing aggregated fact reader. */
@Repository
public class PoiPnlFactSourceReader implements PnlFactSourceReader {
    private final ImportFileRepository fileRepository;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public PoiPnlFactSourceReader(ImportFileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public List<PnlFactSource> readSources(AnalysisBatch batch, ImportTask task) {
        String taskNo = task == null ? "null" : task.getTaskNo();
        try (InputStream input = fileRepository.open(taskNo);
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            List<PnlFactSource> sources = new ArrayList<PnlFactSource>();
            if (header == null) return sources;

            Map<String, Integer> columns = headers(header);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                String scope = scope(row, columns, batch.getPerspective());
                if (scope.isEmpty()) continue;

                sources.add(new PnlFactSource(
                        task.getTaskNo(),
                        task.getFileName(),
                        task.getChecksum(),
                        sheet.getSheetName(),
                        row.getRowNum() + 1,
                        scope,
                        firstText(row, columns, "收入计算基地", "基地", "出货基地"),
                        firstText(row, columns, "产品线", "电芯型号", "产品类别", "客户项目改", "客户项目"),
                        firstText(row, columns, "拉线", "线体", "产线")));
            }
            return sources;
        } catch (Exception ex) {
            throw new IllegalStateException("解析导入来源失败: " + taskNo + ", " + ex.getMessage(), ex);
        }
    }

    private String scope(Row row, Map<String, Integer> columns, AnalysisPerspective perspective) {
        if (perspective == AnalysisPerspective.BASE) {
            return firstText(row, columns, "收入计算基地", "基地", "出货基地");
        }
        if (perspective == AnalysisPerspective.PRODUCT_LINE) {
            return firstText(row, columns, "产品线", "客户项目", "项目");
        }
        return firstText(row, columns, "事业部", "客户项目", "项目");
    }

    private Map<String, Integer> headers(Row row) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            String text = text(row.getCell(i));
            if (!text.isEmpty()) result.put(normalize(text), i);
        }
        return result;
    }

    private String firstText(Row row, Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(normalize(name));
            if (index != null) {
                String value = text(row.getCell(index));
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }

    private String text(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}

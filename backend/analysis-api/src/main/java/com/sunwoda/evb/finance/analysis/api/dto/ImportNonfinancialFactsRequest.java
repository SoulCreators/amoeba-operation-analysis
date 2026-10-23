package com.sunwoda.evb.finance.analysis.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 非财经指标（电芯优率）长表导入请求载荷。
 *
 * <p>期间由服务端按批次回填，导入端不传期间；{@code targetActual} 取值 TARGET/ACTUAL。</p>
 */
public class ImportNonfinancialFactsRequest {
    private List<Item> items;

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    /** 单条目标/实际事实行。 */
    public static class Item {
        private String baseCode;
        private String productionLine;
        private String cellModel;
        private String qualityCategory;
        private String targetActual;
        private BigDecimal qualityValue;
        private int sourceRowNo;
        private String effectiveFileVersion;

        public String getBaseCode() { return baseCode; }
        public void setBaseCode(String baseCode) { this.baseCode = baseCode; }
        public String getProductionLine() { return productionLine; }
        public void setProductionLine(String productionLine) { this.productionLine = productionLine; }
        public String getCellModel() { return cellModel; }
        public void setCellModel(String cellModel) { this.cellModel = cellModel; }
        public String getQualityCategory() { return qualityCategory; }
        public void setQualityCategory(String qualityCategory) { this.qualityCategory = qualityCategory; }
        public String getTargetActual() { return targetActual; }
        public void setTargetActual(String targetActual) { this.targetActual = targetActual; }
        public BigDecimal getQualityValue() { return qualityValue; }
        public void setQualityValue(BigDecimal qualityValue) { this.qualityValue = qualityValue; }
        public int getSourceRowNo() { return sourceRowNo; }
        public void setSourceRowNo(int sourceRowNo) { this.sourceRowNo = sourceRowNo; }
        public String getEffectiveFileVersion() { return effectiveFileVersion; }
        public void setEffectiveFileVersion(String effectiveFileVersion) { this.effectiveFileVersion = effectiveFileVersion; }
    }
}

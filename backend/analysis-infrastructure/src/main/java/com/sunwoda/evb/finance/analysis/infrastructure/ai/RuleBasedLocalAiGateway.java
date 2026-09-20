package com.sunwoda.evb.finance.analysis.infrastructure.ai;

import com.sunwoda.evb.finance.analysis.application.port.LocalAiGateway;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地AI未接入前的确定性兜底实现。正式环境替换为公司本地模型Gateway，保持接口不变。
 */
@Primary
@Component
public class RuleBasedLocalAiGateway implements LocalAiGateway {
    @Override
    public Map<String, String> generate(CalculationResult result) {
        BigDecimal revenueGap = BigDecimal.ZERO;
        BigDecimal profitGap = BigDecimal.ZERO;
        BigDecimal costGap = BigDecimal.ZERO;
        for (PnlResult pnl : result.getResults()) {
            revenueGap = revenueGap.add(value(pnl, "GAP_REVENUE"));
            profitGap = profitGap.add(value(pnl, "GAP_NET_PROFIT"));
            costGap = costGap.add(value(pnl, "GAP_SALES_COST"));
        }
        Map<String, String> sections = new LinkedHashMap<String, String>();
        sections.put("经营总述", summary(revenueGap, profitGap));
        sections.put("未达标", profitGap.signum() < 0
                ? "净利润低于预算，建议进入损益结果和PVM页面核查影响范围。" : "当前汇总净利润未低于预算。");
        sections.put("亮点", revenueGap.signum() > 0
                ? "营业收入高于预算，收入端形成正向贡献。" : "收入暂未形成预算正向贡献，需结合业务结构分析。");
        sections.put("原因线索", "系统识别收入差异、销售成本差异和净利润差异，建议按授权明细继续下钻。");
        sections.put("风险", costGap.signum() > 0
                ? "销售成本高于预算，存在毛利被成本侵蚀的风险。" : "当前销售成本未显示高于预算的汇总风险。");
        sections.put("行动建议", "由财经确认差异原因，完成PVM勾稽后再发布AI分析，并将高影响事项纳入下一期经营跟踪。");
        return sections;
    }

    private BigDecimal value(PnlResult pnl, String key) {
        BigDecimal value = pnl.getLines().get(key);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String summary(BigDecimal revenueGap, BigDecimal profitGap) {
        return "本批次收入预算差额为" + revenueGap.stripTrailingZeros().toPlainString()
                + "，净利润预算差额为" + profitGap.stripTrailingZeros().toPlainString()
                + "；以上为已计算结果的规则化摘要，需由财经确认后发布。";
    }
}

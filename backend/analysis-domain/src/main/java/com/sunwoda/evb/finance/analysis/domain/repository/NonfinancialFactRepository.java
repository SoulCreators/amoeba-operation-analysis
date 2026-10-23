package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;

import java.util.List;

/**
 * 非财经指标事实持久化端口。
 *
 * <p>「同期重传整期替换」：{@link #replaceByPeriod(String, List)} 先清空该期间全部事实，
 * 再写入新事实；唯一键「期间＋基地＋拉线＋型号＋类目＋目标/实际」在数据库侧兜底重复阻断。</p>
 */
public interface NonfinancialFactRepository {
    void replaceByPeriod(String period, List<NonfinancialFact> facts);

    List<NonfinancialFact> findByPeriod(String period);
}

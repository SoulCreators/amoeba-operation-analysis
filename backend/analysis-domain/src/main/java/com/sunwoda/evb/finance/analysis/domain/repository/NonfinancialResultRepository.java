package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;

import java.util.List;

/**
 * 非财经指标配对结果持久化端口。计算后按期间整期替换结果。
 */
public interface NonfinancialResultRepository {
    void replaceByPeriod(String period, List<NonfinancialResult> results);

    List<NonfinancialResult> findByPeriod(String period);
}

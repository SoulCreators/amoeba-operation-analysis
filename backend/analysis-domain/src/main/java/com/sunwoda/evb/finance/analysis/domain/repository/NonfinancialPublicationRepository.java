package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;

import java.util.Optional;

/**
 * 非财经指标发布记录持久化端口，按期间唯一。
 */
public interface NonfinancialPublicationRepository {
    /** 按期间保存（不存在则插入，存在则更新）。 */
    NonfinancialPublication save(NonfinancialPublication publication);

    /** 按期间查询发布记录。 */
    Optional<NonfinancialPublication> findByPeriod(String period);
}

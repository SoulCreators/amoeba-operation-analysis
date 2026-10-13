package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;

import java.util.List;
import java.util.Optional;

/** FC 版本及预测快照的写入端口。实现负责事务和数据库并发约束。 */
public interface FcVersionCommandRepository {
    FcVersion create(String versionCode, AnalysisPerspective perspective, String basePeriod,
                     String coverageStart, String coverageEnd, String operator);

    Optional<FcVersion> find(String versionCode, AnalysisPerspective perspective, String basePeriod);

    FcVersion publish(String versionCode, AnalysisPerspective perspective, String basePeriod,
                      String operator);

    int upsertSnapshots(String versionCode, AnalysisPerspective perspective, String basePeriod,
                        List<FcSnapshot> snapshots, String sourceTaskNo);
}

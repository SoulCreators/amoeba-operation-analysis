package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.FcSnapshotTemplateParser;
import com.sunwoda.evb.finance.analysis.application.port.FcVersionCommandRepository;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshotImportPayload;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class FcVersionCommandServiceImpl implements FcVersionCommandService {
    private final FcVersionCommandRepository repository;
    private final FcSnapshotTemplateParser parser;

    @Autowired
    public FcVersionCommandServiceImpl(
            @Autowired(required = false) FcVersionCommandRepository repository,
            @Autowired(required = false) FcSnapshotTemplateParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    @Override
    public FcVersion create(String versionCode, AnalysisPerspective perspective, String basePeriod,
                            String coverageStart, String coverageEnd, String operator) {
        requireRepository();
        requireText(versionCode, "versionCode");
        requireText(basePeriod, "basePeriod");
        requireText(coverageStart, "coverageStart");
        requireText(coverageEnd, "coverageEnd");
        requireText(operator, "operator");
        if (perspective == null) throw new IllegalArgumentException("perspective不能为空");
        return repository.create(versionCode.trim(), perspective, basePeriod.trim(),
                coverageStart.trim(), coverageEnd.trim(), operator.trim());
    }

    @Override
    public ImportValidationResult uploadSnapshots(String versionCode, AnalysisPerspective perspective,
                                                  String basePeriod, InputStream inputStream, String operator) {
        requireRepository();
        if (parser == null) throw new IllegalStateException("当前环境未配置 FC 快照模板解析器");
        requireText(versionCode, "versionCode");
        requireText(basePeriod, "basePeriod");
        requireText(operator, "operator");
        if (perspective == null) throw new IllegalArgumentException("perspective不能为空");
        if (inputStream == null) throw new IllegalArgumentException("FC 快照文件不能为空");
        FcVersion version = repository.find(versionCode.trim(), perspective, basePeriod.trim())
                .orElseThrow(() -> new IllegalArgumentException("FC版本不存在: " + versionCode));
        if ("PUBLISHED".equalsIgnoreCase(version.getStatus()) || version.isOfficial()) {
            throw new IllegalStateException("正式 FC 版本不可覆盖，请新建版本后再发布");
        }
        String taskNo = "FC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        FcSnapshotImportPayload payload = parser.parse(inputStream, taskNo);
        if (!payload.getIssues().isEmpty()) {
            return new ImportValidationResult(taskNo, ImportStatus.INVALID, payload.getRowCount(),
                    payload.getHeaders(), payload.getIssues());
        }
        if (payload.getSnapshots().isEmpty()) {
            List<ImportIssue> issues = new ArrayList<ImportIssue>();
            issues.add(new ImportIssue(0, "文件", "EMPTY_FILE", "FC快照文件没有有效数据行", ""));
            return new ImportValidationResult(taskNo, ImportStatus.INVALID, payload.getRowCount(),
                    payload.getHeaders(), issues);
        }
        int count = repository.upsertSnapshots(versionCode.trim(), perspective, basePeriod.trim(),
                payload.getSnapshots(), taskNo);
        return new ImportValidationResult(taskNo, ImportStatus.VALID, count,
                payload.getHeaders(), Collections.<ImportIssue>emptyList());
    }

    @Override
    public FcVersion publish(String versionCode, AnalysisPerspective perspective, String basePeriod,
                             String operator) {
        requireRepository();
        requireText(versionCode, "versionCode");
        requireText(basePeriod, "basePeriod");
        requireText(operator, "operator");
        if (perspective == null) throw new IllegalArgumentException("perspective不能为空");
        FcVersion version = repository.find(versionCode.trim(), perspective, basePeriod.trim())
                .orElseThrow(() -> new IllegalArgumentException("FC版本不存在: " + versionCode));
        if ("PUBLISHED".equalsIgnoreCase(version.getStatus()) && version.isOfficial()) return version;
        return repository.publish(versionCode.trim(), perspective, basePeriod.trim(), operator.trim());
    }

    private void requireRepository() {
        if (repository == null) throw new IllegalStateException("当前环境未配置 FC 版本数据库写入能力");
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }
}

package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshotImportPayload;

import java.io.InputStream;

/** 解析 FC 快照导入模板，隔离 POI 等文件格式依赖。 */
public interface FcSnapshotTemplateParser {
    FcSnapshotImportPayload parse(InputStream inputStream, String taskNo);
}

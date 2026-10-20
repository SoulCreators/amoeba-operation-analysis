package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.FcSnapshotTemplateParser;
import com.sunwoda.evb.finance.analysis.application.port.FcVersionCommandRepository;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshotImportPayload;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 本次修改补齐 FC 快照上传无效结果的契约测试，锁定校验失败时不写入快照的行为。
 */
class FcVersionCommandServiceImplUploadInvalidContractTest {
    private static final String VERSION_CODE = "FC-2026-10-001";
    private static final String BASE_PERIOD = "2026-10";

    /** 验证 parser 返回 issues 时完整透传无效结果，并且绝不执行快照 upsert。 */
    @Test
    void parserIssuesReturnInvalidAndPreserveValidationDetailsWithoutUpsert() {
        FcVersionCommandRepository repository = mock(FcVersionCommandRepository.class);
        FcSnapshotTemplateParser parser = mock(FcSnapshotTemplateParser.class);
        when(repository.find(VERSION_CODE, AnalysisPerspective.PRODUCT_LINE, BASE_PERIOD))
                .thenReturn(Optional.of(draftVersion()));

        List<String> headers = Arrays.asList("产品线", "预测值");
        ImportIssue issue = new ImportIssue(3, "预测值", "INVALID_NUMBER", "预测值不是数字", "abc");
        when(parser.parse(any(InputStream.class), anyString())).thenAnswer(invocation ->
                new FcSnapshotImportPayload(invocation.getArgument(1), 5, headers,
                        Collections.emptyList(), Collections.singletonList(issue)));

        FcVersionCommandServiceImpl service = new FcVersionCommandServiceImpl(repository, parser);
        ImportValidationResult result = service.uploadSnapshots(VERSION_CODE,
                AnalysisPerspective.PRODUCT_LINE, BASE_PERIOD,
                new ByteArrayInputStream(new byte[]{1, 2, 3}), "tester");

        assertEquals(ImportStatus.INVALID, result.getStatus());
        ArgumentCaptor<String> taskNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(parser).parse(any(InputStream.class), taskNoCaptor.capture());
        assertEquals(taskNoCaptor.getValue(), result.getTaskNo());
        assertEquals(5, result.getRowCount());
        assertEquals(headers, result.getHeaders());
        assertEquals(1, result.getIssues().size());
        assertSame(issue, result.getIssues().get(0));
        verify(repository, never()).upsertSnapshots(anyString(), any(AnalysisPerspective.class),
                anyString(), anyList(), anyString());
    }

    /** 验证无有效快照且无 parser issues 时生成 EMPTY_FILE，并且绝不执行快照 upsert。 */
    @Test
    void emptySnapshotsReturnInvalidWithEmptyFileIssueWithoutUpsert() {
        FcVersionCommandRepository repository = mock(FcVersionCommandRepository.class);
        FcSnapshotTemplateParser parser = mock(FcSnapshotTemplateParser.class);
        when(repository.find(VERSION_CODE, AnalysisPerspective.PRODUCT_LINE, BASE_PERIOD))
                .thenReturn(Optional.of(draftVersion()));

        List<String> headers = Arrays.asList("产品线", "预测值");
        when(parser.parse(any(InputStream.class), anyString())).thenAnswer(invocation ->
                new FcSnapshotImportPayload(invocation.getArgument(1), 0, headers,
                        Collections.emptyList(), Collections.emptyList()));

        FcVersionCommandServiceImpl service = new FcVersionCommandServiceImpl(repository, parser);
        ImportValidationResult result = service.uploadSnapshots(VERSION_CODE,
                AnalysisPerspective.PRODUCT_LINE, BASE_PERIOD,
                new ByteArrayInputStream(new byte[]{4, 5, 6}), "tester");

        assertEquals(ImportStatus.INVALID, result.getStatus());
        ArgumentCaptor<String> taskNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(parser).parse(any(InputStream.class), taskNoCaptor.capture());
        assertEquals(taskNoCaptor.getValue(), result.getTaskNo());
        assertEquals(0, result.getRowCount());
        assertEquals(headers, result.getHeaders());
        assertEquals(1, result.getIssues().size());
        assertEquals("EMPTY_FILE", result.getIssues().get(0).getIssueCode());
        verify(repository, never()).upsertSnapshots(anyString(), any(AnalysisPerspective.class),
                anyString(), anyList(), anyString());
    }

    /** 本次补强验证即使 payload 同时含有效快照和 issues，也必须优先返回 INVALID，禁止部分写入。 */
    @Test
    void snapshotsWithIssuesReturnInvalidWithoutPartialUpsert() {
        FcVersionCommandRepository repository = mock(FcVersionCommandRepository.class);
        FcSnapshotTemplateParser parser = mock(FcSnapshotTemplateParser.class);
        when(repository.find(VERSION_CODE, AnalysisPerspective.PRODUCT_LINE, BASE_PERIOD))
                .thenReturn(Optional.of(draftVersion()));

        List<String> headers = Arrays.asList("产品线", "预测值");
        FcSnapshot snapshot = new FcSnapshot(BASE_PERIOD, "PL-001", "FC_VALUE",
                new BigDecimal("123.45"));
        ImportIssue issue = new ImportIssue(2, "预测值", "INVALID_NUMBER", "预测值不是数字", "abc");
        when(parser.parse(any(InputStream.class), anyString())).thenAnswer(invocation ->
                new FcSnapshotImportPayload(invocation.getArgument(1), 2, headers,
                        Collections.singletonList(snapshot), Collections.singletonList(issue)));

        FcVersionCommandServiceImpl service = new FcVersionCommandServiceImpl(repository, parser);
        ImportValidationResult result = service.uploadSnapshots(VERSION_CODE,
                AnalysisPerspective.PRODUCT_LINE, BASE_PERIOD,
                new ByteArrayInputStream(new byte[]{7, 8, 9}), "tester");

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(1, result.getIssues().size());
        assertSame(issue, result.getIssues().get(0));
        verify(repository, never()).upsertSnapshots(anyString(), any(AnalysisPerspective.class),
                anyString(), anyList(), anyString());
    }

    /** 本次补强验证三个必填参数缺失时立即抛出对应异常，repository 与 parser 均不发生交互。 */
    @ParameterizedTest(name = "缺失{3}时抛出对应异常")
    @MethodSource("missingUploadArgumentCases")
    void missingUploadArgumentThrowsWithoutRepositoryOrParserInteraction(
            String versionCode, String basePeriod, String operator, String fieldName) {
        FcVersionCommandRepository repository = mock(FcVersionCommandRepository.class);
        FcSnapshotTemplateParser parser = mock(FcSnapshotTemplateParser.class);
        FcVersionCommandServiceImpl service = new FcVersionCommandServiceImpl(repository, parser);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> service.uploadSnapshots(versionCode, AnalysisPerspective.PRODUCT_LINE,
                        basePeriod, new ByteArrayInputStream(new byte[]{1}), operator));

        assertEquals(fieldName + "不能为空", thrown.getMessage());
        verifyNoInteractions(repository, parser);
    }

    private static Stream<Arguments> missingUploadArgumentCases() {
        return Stream.of(
                Arguments.of(null, BASE_PERIOD, "tester", "versionCode"),
                Arguments.of(VERSION_CODE, null, "tester", "basePeriod"),
                Arguments.of(VERSION_CODE, BASE_PERIOD, null, "operator"));
    }

    private static FcVersion draftVersion() {
        return new FcVersion(VERSION_CODE, AnalysisPerspective.PRODUCT_LINE, BASE_PERIOD,
                "2026-10-01", "2026-12-31", "DRAFT", "tester", LocalDateTime.now(),
                false, null, null);
    }
}

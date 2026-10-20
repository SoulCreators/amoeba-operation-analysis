package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Mockito contract coverage for MetricServiceImpl's current repository-backed behavior. */
class MetricServiceImplContractTest {
    private MetricDefinitionRepository repository;
    private MetricServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(MetricDefinitionRepository.class);
        service = new MetricServiceImpl(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingMetricCode(String code) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsert(code, "营业收入", "ALL", "CNY",
                        "SUM(REVENUE)", 1, true, "收入", "tester"));

        assertEquals("metricCode不能为空", error.getMessage());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingMetricName(String name) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsert("REVENUE", name, "ALL", "CNY",
                        "SUM(REVENUE)", 1, true, "收入", "tester"));

        assertEquals("metricName不能为空", error.getMessage());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingFormula(String formula) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsert("REVENUE", "营业收入", "ALL", "CNY",
                        formula, 1, true, "收入", "tester"));

        assertEquals("formula不能为空", error.getMessage());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingUpdatedBy(String updatedBy) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upsert("REVENUE", "营业收入", "ALL", "CNY",
                        "SUM(REVENUE)", 1, true, "收入", updatedBy));

        assertEquals("updatedBy不能为空", error.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void createsDefinitionWhenRepositoryHasNoMatchingCode() {
        when(repository.findByCode("REVENUE")).thenReturn(Optional.empty());
        when(repository.save(any(MetricDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MetricDefinition result = service.upsert("REVENUE", "营业收入", "ALL", "CNY",
                "SUM(REVENUE)", 1, true, "收入摘要", "tester");

        ArgumentCaptor<MetricDefinition> saved = ArgumentCaptor.forClass(MetricDefinition.class);
        verify(repository).findByCode("REVENUE");
        verify(repository).save(saved.capture());
        MetricDefinition definition = saved.getValue();
        assertSame(definition, result);
        assertEquals("REVENUE", definition.getMetricCode());
        assertEquals("营业收入", definition.getMetricName());
        assertEquals("ALL", definition.getApplicablePerspective());
        assertEquals("CNY", definition.getUnit());
        assertEquals("SUM(REVENUE)", definition.getFormulaExpression());
        assertEquals(1, definition.getDisplayOrder());
        assertEquals(true, definition.isEnabled());
        assertEquals("收入摘要", definition.getAiSummaryTemplate());
        assertEquals("tester", definition.getUpdatedBy());
        assertNotNull(definition.getUpdatedAt());
    }

    @Test
    void updatesExistingDefinitionAndSavesTheSameEntity() {
        MetricDefinition existing = new MetricDefinition(7L, "REVENUE", "旧名称", "BASE",
                "CNY", "OLD_FORMULA", 9, false, "旧摘要", "old-user",
                LocalDateTime.of(2025, 1, 1, 0, 0));
        when(repository.findByCode("REVENUE")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        MetricDefinition result = service.upsert("REVENUE", "营业收入", "ALL", "%",
                "SUM(REVENUE)", 2, true, "新摘要", "tester");

        verify(repository).findByCode("REVENUE");
        verify(repository).save(existing);
        assertSame(existing, result);
        assertEquals(Long.valueOf(7L), existing.getId());
        assertEquals("营业收入", existing.getMetricName());
        assertEquals("ALL", existing.getApplicablePerspective());
        assertEquals("%", existing.getUnit());
        assertEquals("SUM(REVENUE)", existing.getFormulaExpression());
        assertEquals(2, existing.getDisplayOrder());
        assertEquals(true, existing.isEnabled());
        assertEquals("新摘要", existing.getAiSummaryTemplate());
        assertEquals("tester", existing.getUpdatedBy());
        assertNotNull(existing.getUpdatedAt());
    }

    @Test
    void looksUpBeforeSavingDuringUpsert() {
        when(repository.findByCode("REVENUE")).thenReturn(Optional.empty());
        when(repository.save(any(MetricDefinition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert("REVENUE", "营业收入", "ALL", "CNY",
                "SUM(REVENUE)", 1, true, "收入摘要", "tester");

        InOrder interactions = inOrder(repository);
        interactions.verify(repository).findByCode("REVENUE");
        interactions.verify(repository).save(any(MetricDefinition.class));
    }

    @Test
    void passesListPerspectiveAndIncludeDisabledToRepository() {
        List<MetricDefinition> enabledOnly = Collections.singletonList(
                metric("REVENUE", true));
        List<MetricDefinition> allDefinitions = Arrays.asList(
                metric("REVENUE", true), metric("OLD_REVENUE", false));
        when(repository.list("BASE", false)).thenReturn(enabledOnly);
        when(repository.list(null, true)).thenReturn(allDefinitions);

        assertSame(enabledOnly, service.list("BASE", false));
        assertSame(allDefinitions, service.list(null, true));

        verify(repository).list("BASE", false);
        verify(repository).list(null, true);
    }

    private MetricDefinition metric(String code, boolean enabled) {
        return new MetricDefinition(1L, code, code, "ALL", "CNY",
                "SUM(" + code + ")", 1, enabled, "摘要", "tester", LocalDateTime.now());
    }
}

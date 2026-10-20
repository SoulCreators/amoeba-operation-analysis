package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingServiceImplContractTest {
    private static final MappingType TYPE = MappingType.SALES_PROJECT;
    private static final String MATCH_KEY = "project-001";
    private static final String MAPPED_VALUE = "阿米巴一部";
    private static final String UPDATED_BY = "operator";

    @Mock
    private MappingEntryRepository repository;

    private MappingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MappingServiceImpl(repository);
    }

    @Test
    void upsertRejectsMissingRequiredFieldsBeforeRepositoryAccess() {
        assertRequiredField("映射类型", () -> service.upsert(null, MATCH_KEY, MAPPED_VALUE,
                true, UPDATED_BY));
        assertRequiredField("matchKey", () -> service.upsert(TYPE, " ", MAPPED_VALUE,
                true, UPDATED_BY));
        assertRequiredField("mappedValue", () -> service.upsert(TYPE, MATCH_KEY, null,
                true, UPDATED_BY));
        assertRequiredField("updatedBy", () -> service.upsert(TYPE, MATCH_KEY, MAPPED_VALUE,
                true, ""));

        verifyNoInteractions(repository);
    }

    @Test
    void upsertCreatesEntryWhenRepositoryHasNoMatch() {
        when(repository.find(TYPE, MATCH_KEY)).thenReturn(Optional.empty());
        when(repository.save(any(MappingEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MappingEntry result = service.upsert(TYPE, MATCH_KEY, MAPPED_VALUE,
                true, UPDATED_BY);

        ArgumentCaptor<MappingEntry> captor = ArgumentCaptor.forClass(MappingEntry.class);
        verify(repository).find(TYPE, MATCH_KEY);
        verify(repository).save(captor.capture());
        MappingEntry saved = captor.getValue();

        assertSame(saved, result);
        assertEquals(TYPE, saved.getType());
        assertEquals(MATCH_KEY, saved.getMatchKey());
        assertEquals(MAPPED_VALUE, saved.getMappedValue());
        assertTrue(saved.isEnabled());
        assertEquals(UPDATED_BY, saved.getUpdatedBy());
        assertTrue(saved.getUpdatedAt() != null);
    }

    @Test
    void upsertUpdatesExistingEntryAndSavesIt() {
        MappingEntry existing = new MappingEntry(8L, TYPE, MATCH_KEY, "旧映射值",
                false, "old-operator", LocalDateTime.now().minusMinutes(1));
        when(repository.find(TYPE, MATCH_KEY)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        MappingEntry result = service.upsert(TYPE, MATCH_KEY, MAPPED_VALUE,
                true, UPDATED_BY);

        assertSame(existing, result);
        assertEquals(8L, result.getId());
        assertEquals(MAPPED_VALUE, result.getMappedValue());
        assertTrue(result.isEnabled());
        assertEquals(UPDATED_BY, result.getUpdatedBy());
        verify(repository).find(TYPE, MATCH_KEY);
        verify(repository).save(existing);
    }

    @Test
    void disableMarksExistingEntryDisabledAndSavesIt() {
        MappingEntry existing = new MappingEntry(9L, TYPE, MATCH_KEY, MAPPED_VALUE,
                true, "old-operator", LocalDateTime.now().minusMinutes(1));
        when(repository.find(TYPE, MATCH_KEY)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        MappingEntry result = service.disable(TYPE, MATCH_KEY, UPDATED_BY);

        assertSame(existing, result);
        assertFalse(result.isEnabled());
        assertEquals(MAPPED_VALUE, result.getMappedValue());
        assertEquals(UPDATED_BY, result.getUpdatedBy());
        verify(repository).find(TYPE, MATCH_KEY);
        verify(repository).save(existing);
    }

    @Test
    void disableRejectsMissingEntryWithoutSaving() {
        when(repository.find(TYPE, MATCH_KEY)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.disable(TYPE, MATCH_KEY, UPDATED_BY));

        assertEquals("映射不存在: " + MATCH_KEY, exception.getMessage());
        verify(repository).find(TYPE, MATCH_KEY);
        verify(repository, never()).save(any(MappingEntry.class));
    }

    @Test
    void listPassesTypeAndIncludeDisabledToRepository() {
        List<MappingEntry> expected = Arrays.asList(
                new MappingEntry(1L, TYPE, "project-001", MAPPED_VALUE,
                        true, UPDATED_BY, LocalDateTime.now()),
                new MappingEntry(2L, TYPE, "project-002", "阿米巴二部",
                        false, UPDATED_BY, LocalDateTime.now()));
        when(repository.list(TYPE, true)).thenReturn(expected);

        List<MappingEntry> result = service.list(TYPE, true);

        assertSame(expected, result);
        verify(repository).list(TYPE, true);
    }

    @Test
    void listRejectsNullTypeWithoutRepositoryAccess() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.list(null, false));

        assertEquals("映射类型不能为空", exception.getMessage());
        verifyNoInteractions(repository);
    }

    private void assertRequiredField(String field, org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertEquals(field + "不能为空", exception.getMessage());
    }
}

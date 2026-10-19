package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcUserScopeRepositoryTest {
    private JdbcTemplate jdbc;
    private JdbcUserScopeRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        repository = new JdbcUserScopeRepository(mock(DataSource.class));
        Field field = JdbcUserScopeRepository.class.getDeclaredField("jdbc");
        field.setAccessible(true);
        field.set(repository, jdbc);
    }

    @Test
    void savesDetailExportConfigAndEnabledFlags() {
        UserScope scope = new UserScope("U001", "BASE_FINANCE", AnalysisPerspective.BASE,
                "NANCHANG", true, false, true, false);

        assertEquals(scope, repository.save(scope));

        verify(jdbc).update(anyString(), eq("U001"), eq("BASE_FINANCE"), eq("BASE"),
                eq("NANCHANG"), eq(true), eq(false), eq(true), eq(false));
    }

    @Test
    void readsAllPermissionFlagsAndOnlyEnabledRows() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("user_id")).thenReturn("U001");
        when(rs.getString("role_code")).thenReturn("BASE_FINANCE");
        when(rs.getString("perspective")).thenReturn("BASE");
        when(rs.getString("scope_code")).thenReturn("NANCHANG");
        when(rs.getBoolean("can_view_detail")).thenReturn(true);
        when(rs.getBoolean("can_export")).thenReturn(false);
        when(rs.getBoolean("can_edit_config")).thenReturn(true);
        when(rs.getBoolean("enabled")).thenReturn(true);
        doAnswer(invocation -> {
            RowMapper<UserScope> mapper = invocation.getArgument(1);
            return Collections.singletonList(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), eq("U001"), eq("BASE"));

        List<UserScope> scopes = repository.findByUserAndPerspective("U001", AnalysisPerspective.BASE);

        assertEquals(1, scopes.size());
        assertTrue(scopes.get(0).isCanViewDetail());
        assertFalse(scopes.get(0).isCanExport());
        assertTrue(scopes.get(0).isCanEditConfig());
        assertTrue(scopes.get(0).isEnabled());
        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("can_view_detail"),
                any(RowMapper.class), eq("U001"), eq("BASE"));
        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("enabled = TRUE"),
                any(RowMapper.class), eq("U001"), eq("BASE"));
    }
}

package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcPnlFactDetailProviderTest {
    private JdbcTemplate jdbc;
    private JdbcPnlFactDetailProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        provider = new JdbcPnlFactDetailProvider(mock(DataSource.class));
        Field field = JdbcPnlFactDetailProvider.class.getDeclaredField("jdbc");
        field.setAccessible(true);
        field.set(provider, jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("B-001"), eq("BASE"), eq("U001")))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void detailQueryRequiresDetailPermissionAndAuthorizedScope() {
        provider.findForUser("B-001", AnalysisPerspective.BASE, "U001");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("B-001"), eq("BASE"), eq("U001"));
        assertTrue(sql.getValue().contains("s.can_view_detail = TRUE"));
        assertTrue(sql.getValue().contains("s.scope_code = 'ALL' OR s.scope_code = f.scope_code"));
        assertTrue(sql.getValue().contains("s.enabled = TRUE"));
        assertTrue(sql.getValue().contains("f.material_loss"));
        assertTrue(sql.getValue().contains("f.budget_material_loss"));
    }
}

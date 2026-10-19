package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline Spring component/profile contract for the JDBC audit-event adapter. */
class AuditEventDbProfileWiringContractTest {

    @Test
    void dbProfileExposesTheJdbcAdapterThroughTheAuditPort() {
        assertTrue(AuditEventPort.class.isAssignableFrom(JdbcAuditEventRepository.class));

        Repository repository = JdbcAuditEventRepository.class.getAnnotation(Repository.class);
        assertNotNull(repository);
        assertEquals("", repository.value());

        Profile profile = JdbcAuditEventRepository.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[] {"db"}, profile.value());
    }
}

package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline contract for the V020 audit-event schema and verification probe. */
class DatabaseV020AuditEventContractTest {
    private static final String MIGRATION =
            "database/migration/V020__create_audit_event.sql";
    private static final String ROLLBACK =
            "database/rollback/V020__drop_audit_event.sql";
    private static final String VERIFICATION =
            "database/verification/check_audit_event.sql";

    @Test
    void migrationDeclaresUniqueEventIdAndObjectOnlyMetadata() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));

        assertTrue(migration.contains("event_id VARCHAR(128) PRIMARY KEY"),
                "event_id must remain unique through the primary key");
        assertTrue(migration.contains("metadata JSONB NOT NULL DEFAULT '{}'::jsonb"),
                "metadata must be a non-null JSON object column");
        assertTrue(migration.contains("ck_audit_event_metadata_object"),
                "metadata object constraint must be named and present");
        assertTrue(migration.contains("jsonb_typeof(metadata) = 'object'"),
                "metadata must reject JSON arrays and scalar values");
    }

    @Test
    void verificationProbeCoversDuplicateAndNonObjectRejectionTransactionally() throws IOException {
        String verification = read(repositoryRoot().resolve(VERIFICATION));

        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"),
                "verification must not leave probe rows committed");
        assertTrue(verification.contains("WHEN unique_violation"),
                "verification must prove duplicate event_id is rejected");
        assertTrue(verification.contains("Duplicate audit event_id was accepted"),
                "duplicate event_id rejection must fail the verification when absent");
        assertTrue(verification.contains("'[]'::jsonb"),
                "verification must probe non-object JSON metadata");
        assertTrue(verification.contains("WHEN check_violation"),
                "verification must prove non-object metadata is rejected");
        assertTrue(verification.contains("Non-object audit metadata was accepted"),
                "metadata rejection must fail the verification when absent");
    }

    @Test
    void rollbackDropsOnlyTheAuditEventTable() throws IOException {
        String rollback = read(repositoryRoot().resolve(ROLLBACK));

        assertTrue(rollback.contains("DROP TABLE IF EXISTS audit.audit_event"));
        assertFalse(rollback.contains("DROP SCHEMA"),
                "V020 rollback must not remove the shared audit schema");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve(MIGRATION))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root containing " + MIGRATION);
    }
}

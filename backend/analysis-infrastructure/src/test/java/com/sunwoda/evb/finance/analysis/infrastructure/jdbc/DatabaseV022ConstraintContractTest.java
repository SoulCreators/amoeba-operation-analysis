package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline contract for the V022 database hardening write set. */
class DatabaseV022ConstraintContractTest {
    private static final String MIGRATION =
            "database/migration/V022__harden_batch_import_status_constraints.sql";
    private static final String ROLLBACK =
            "database/rollback/V022__harden_batch_import_status_constraints.sql";
    private static final String VERIFICATION =
            "database/verification/check_batch_import_constraints.sql";

    @Test
    void migrationMatchesJavaEnumValuesAndAddsOnlyTheRequestedChecks() throws IOException {
        Path root = repositoryRoot();
        String migration = read(root.resolve(MIGRATION));

        for (String value : Arrays.asList(
                "DRAFT", "CHECKING", "READY_FOR_CALCULATION", "CALCULATING",
                "CALCULATED", "CONFIRMED", "PUBLISHED", "FAILED",
                "CREATED", "VALIDATING", "VALID", "INVALID", "CANCELLED")) {
            assertTrue(migration.contains("'" + value + "'"),
                    "V022 must include Java enum value " + value);
        }

        for (String constraint : Arrays.asList(
                "ck_analysis_batch_status",
                "ck_import_task_status",
                "ck_import_task_issue_count_nonnegative",
                "ck_import_task_valid_issue_count")) {
            assertTrue(migration.contains(constraint),
                    "V022 must define " + constraint);
        }

        assertTrue(migration.contains("issue_count >= 0"));
        assertTrue(migration.contains("status <> 'VALID' OR issue_count = 0"));
        assertFalse(migration.matches("(?ms).*^\\s*(UPDATE|INSERT|DELETE)\\b.*$"),
                "V022 must not backfill or rewrite historical rows");
    }

    @Test
    void rollbackAndVerificationAreScopedAndTransactional() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));
        String verification = read(root.resolve(VERIFICATION));

        for (String constraint : Arrays.asList(
                "ck_analysis_batch_status",
                "ck_import_task_status",
                "ck_import_task_issue_count_nonnegative",
                "ck_import_task_valid_issue_count")) {
            assertTrue(rollback.contains("DROP CONSTRAINT IF EXISTS " + constraint),
                    "rollback must remove only " + constraint);
        }

        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"));
        assertTrue(verification.contains("pg_temp.v022_constraint_verification_context"));
        assertTrue(verification.contains("uk_import_task_batch_dataset"),
                "verification must protect the existing idempotency index");
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

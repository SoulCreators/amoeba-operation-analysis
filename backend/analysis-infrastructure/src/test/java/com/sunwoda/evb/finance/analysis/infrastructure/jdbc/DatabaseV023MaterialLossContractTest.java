package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline contract for the V023 material-loss fact retention slice. */
class DatabaseV023MaterialLossContractTest {
    private static final String MIGRATION =
            "database/migration/V023__add_material_loss_fact_columns.sql";
    private static final String VERIFICATION =
            "database/verification/check_pnl_material_loss.sql";

    @Test
    void migrationAddsOnlyNullableFactColumns() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));

        assertTrue(migration.contains("ALTER TABLE raw.pnl_fact"));
        assertTrue(migration.contains("material_loss NUMERIC(24, 8)"));
        assertTrue(migration.contains("budget_material_loss NUMERIC(24, 8)"));
        assertFalse(migration.matches("(?is).*\\b(INSERT|UPDATE|DELETE)\\b.*"),
                "V023 must not rewrite existing facts");
        assertFalse(migration.toLowerCase().contains("pnl_result"),
                "V023 must not change formal result tables");
        assertFalse(migration.toLowerCase().contains("pvm"),
                "V023 must not change formal PVM results");
    }

    @Test
    void verificationChecksBothActualAndBudgetColumns() throws IOException {
        String verification = read(repositoryRoot().resolve(VERIFICATION));

        assertTrue(verification.contains("raw.pnl_fact.material_loss"));
        assertTrue(verification.contains("raw.pnl_fact.budget_material_loss"));
        assertTrue(verification.contains("column_name = 'material_loss'"));
        assertTrue(verification.contains("column_name = 'budget_material_loss'"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve(MIGRATION))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root containing " + MIGRATION);
    }
}

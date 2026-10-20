package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL contract for user-scoped calculation-result reads.
 *
 * <p>The production read contract expected by this test is:
 * {@code Optional<CalculationResult> findByBatchNoForUser(String batchNo, String userId)}.
 * Reflection is intentional so this test can be added before the production interface is
 * merged: the targeted run then reports the missing contract as a test failure instead of
 * requiring this test-only change to modify production code.</p>
 */
class JdbcCalculationResultRepositoryUserScopePostgresContractTest {
    private static final String BATCH_PREFIX = "TEST-RESULT-SCOPE-";
    private static final String USER_PREFIX = "TEST-RESULT-SCOPE-USER-";
    private static final String PERSPECTIVE = "BASE";
    /* V009 currently enforces (batch_id, version_no) uniqueness, so each seeded
       result row gets a deterministic version suffix while the read contract
       still verifies that the first returned version is preserved. */
    private static final String VERSION_PREFIX = "RESULT-SCOPE-V20261020";
    private static final String RESULT_STATUS = "PUBLISHED";

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcCalculationResultRepository repository;
    private static String batchNo;
    private static String singleUser;
    private static String allUser;
    private static String crossScopeUser;
    private static String disabledScopeUser;

    @BeforeAll
    static void connectAndSeedPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; result user-scope PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM meta.analysis_batch", Long.class);
            jdbc.queryForObject("SELECT COUNT(*) FROM calc.pnl_result", Long.class);
            jdbc.queryForObject("SELECT COUNT(*) FROM calc.pnl_result_line", Long.class);
            jdbc.queryForObject("SELECT COUNT(*) FROM iam.user_scope", Long.class);
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: result user-scope PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        repository = new JdbcCalculationResultRepository(dataSource);
        String token = UUID.randomUUID().toString().replace("-", "");
        batchNo = BATCH_PREFIX + token;
        singleUser = USER_PREFIX + token + "-SINGLE";
        allUser = USER_PREFIX + token + "-ALL";
        crossScopeUser = USER_PREFIX + token + "-CROSS";
        disabledScopeUser = USER_PREFIX + token + "-DISABLED";
        seedFixture();
    }

    @AfterAll
    static void cleanupFixture() {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM iam.user_scope WHERE user_id LIKE ?", USER_PREFIX + "%");
        jdbc.update("DELETE FROM calc.pnl_result_line WHERE result_id IN "
                + "(SELECT r.id FROM calc.pnl_result r JOIN meta.analysis_batch b ON b.id = r.batch_id "
                + "WHERE b.batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM calc.pnl_result WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void singleScopeUserSeesOnlyAuthorizedResultAndAllLines() {
        CalculationResult result = scopedRead(singleUser);

        assertEquals(Arrays.asList("SCOPE-A"), scopes(result));
        assertEquals(versionFor("SCOPE-A"), result.getVersionNo());
        assertEquals(CalculationResultStatus.PUBLISHED, result.getStatus());
        assertEquals(0, new BigDecimal("101.25").compareTo(
                result.getResults().get(0).getLines().get("REVENUE")));
        assertEquals(0, new BigDecimal("11.25").compareTo(
                result.getResults().get(0).getLines().get("GROSS_PROFIT")));
    }

    @Test
    void allScopeUserSeesEveryResultAndResultLine() {
        CalculationResult result = scopedRead(allUser);

        assertEquals(Arrays.asList("SCOPE-A", "SCOPE-B", "SCOPE-C"), scopes(result));
        assertEquals(versionFor("SCOPE-A"), result.getVersionNo());
        assertEquals(CalculationResultStatus.PUBLISHED, result.getStatus());
        assertEquals(0, new BigDecimal("303.25").compareTo(result.getResults().stream()
                .map(pnl -> pnl.getLines().get("REVENUE"))
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(new HashSet<String>(Arrays.asList("REVENUE", "GROSS_PROFIT")),
                result.getResults().get(1).getLines().keySet());
    }

    @Test
    void crossScopeUserSeesOnlyEachEnabledAuthorizedScope() {
        CalculationResult result = scopedRead(crossScopeUser);

        assertEquals(Arrays.asList("SCOPE-A", "SCOPE-C"), scopes(result));
        assertFalse(scopes(result).contains("SCOPE-B"));
        assertEquals(versionFor("SCOPE-A"), result.getVersionNo());
        assertEquals(CalculationResultStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void disabledScopeGrantDoesNotExposeTheDisabledScope() {
        Optional<CalculationResult> result = invokeScopedRead(disabledScopeUser);

        assertTrue(!result.isPresent() || result.get().getResults().isEmpty(),
                "a user with only a disabled scope grant must not receive result rows");
    }

    @Test
    void scopedReadMethodMustExistWithAnExplicitUserParameter() throws Exception {
        Method method = findScopedReadMethod();

        assertNotNull(method);
        assertEquals(String.class, method.getParameterTypes()[0]);
        assertEquals(String.class, method.getParameterTypes()[1]);
        assertEquals(Optional.class, method.getReturnType(),
                "scoped reads must preserve the repository Optional contract");
    }

    private static CalculationResult scopedRead(String userId) {
        Optional<CalculationResult> result = invokeScopedRead(userId);
        assertTrue(result.isPresent(), "expected an authorized scoped result for " + userId);
        return result.get();
    }

    @SuppressWarnings("unchecked")
    private static Optional<CalculationResult> invokeScopedRead(String userId) {
        Method method = findScopedReadMethod();
        try {
            Object value = method.invoke(repository, batchNo, userId);
            assertTrue(value instanceof Optional,
                    "findByBatchNoForUser must return Optional<CalculationResult>");
            return (Optional<CalculationResult>) value;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            throw new AssertionError("scoped result read failed for " + userId + ": " + cause, cause);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("cannot invoke findByBatchNoForUser(String, String)", failure);
        }
    }

    private static Method findScopedReadMethod() {
        try {
            return JdbcCalculationResultRepository.class.getMethod(
                    "findByBatchNoForUser", String.class, String.class);
        } catch (NoSuchMethodException missingContract) {
            throw new AssertionError(
                    "BLOCKED: production contract missing: "
                            + "JdbcCalculationResultRepository.findByBatchNoForUser(String, String) "
                            + "must be added by the development role", missingContract);
        }
    }

    private static List<String> scopes(CalculationResult result) {
        return result.getResults().stream()
                .map(pnl -> pnl.getScopeCode())
                .collect(Collectors.toList());
    }

    private static void seedFixture() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
        Long batchId = jdbc.queryForObject("INSERT INTO meta.analysis_batch "
                        + "(batch_no, period, perspective, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                batchNo, "2026-09", PERSPECTIVE, "PUBLISHED", USER_PREFIX + "SEED", now, now);

        Long resultA = insertResult(batchId, "SCOPE-A", now);
        Long resultB = insertResult(batchId, "SCOPE-B", now);
        Long resultC = insertResult(batchId, "SCOPE-C", now);
        insertLine(resultA, "REVENUE", "101.25");
        insertLine(resultA, "GROSS_PROFIT", "11.25");
        insertLine(resultB, "REVENUE", "202.00");
        insertLine(resultB, "GROSS_PROFIT", "22.00");
        insertLine(resultC, "REVENUE", "0.00");
        insertLine(resultC, "GROSS_PROFIT", "0.00");

        grant(singleUser, "SCOPE-A", true);
        grant(allUser, "ALL", true);
        grant(crossScopeUser, "SCOPE-A", true);
        grant(crossScopeUser, "SCOPE-C", true);
        grant(disabledScopeUser, "SCOPE-B", false);
    }

    private static Long insertResult(Long batchId, String scopeCode, LocalDateTime now) {
        return jdbc.queryForObject("INSERT INTO calc.pnl_result "
                        + "(batch_id, perspective, scope_code, result_status, version_no, calculated_at, "
                        + "confirmed_by, confirmed_at, published_by, published_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                batchId, PERSPECTIVE, scopeCode, RESULT_STATUS, versionFor(scopeCode), now,
                USER_PREFIX + "CONFIRM", now.plusMinutes(1), USER_PREFIX + "PUBLISH", now.plusMinutes(2));
    }

    private static void insertLine(Long resultId, String lineCode, String value) {
        jdbc.update("INSERT INTO calc.pnl_result_line(result_id, line_code, line_value) VALUES (?, ?, ?)",
                resultId, lineCode, new BigDecimal(value));
    }

    private static void grant(String userId, String scopeCode, boolean enabled) {
        jdbc.update("INSERT INTO iam.user_scope(user_id, role_code, perspective, scope_code, "
                        + "can_view_detail, can_export, can_edit_config, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, "TEST_RESULT_VIEWER", PERSPECTIVE, scopeCode, true, false, false, enabled);
    }

    private static String versionFor(String scopeCode) {
        return VERSION_PREFIX + "-" + scopeCode;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}

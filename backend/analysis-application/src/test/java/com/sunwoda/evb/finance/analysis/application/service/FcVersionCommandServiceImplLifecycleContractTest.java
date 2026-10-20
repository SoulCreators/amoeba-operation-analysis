package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.FcVersionCommandRepository;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 本次修改补齐 FcVersionCommandServiceImpl 的版本生命周期契约测试，保持生产代码不变。
 */
@ExtendWith(MockitoExtension.class)
class FcVersionCommandServiceImplLifecycleContractTest {
    private static final AnalysisPerspective PERSPECTIVE = AnalysisPerspective.BASE;

    @Mock
    private FcVersionCommandRepository repository;

    private FcVersionCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FcVersionCommandServiceImpl(repository, null);
    }

    /** 验证 create 会 trim 文本参数，并原样返回 repository.create 的对象。 */
    @Test
    void createTrimsTextArgumentsAndReturnsRepositoryResult() {
        FcVersion expected = version("DRAFT", false);
        when(repository.create("FC-001", PERSPECTIVE, "2026-09", "2026-09-01",
                "2026-09-30", "operator")).thenReturn(expected);

        FcVersion result = service.create(" FC-001 ", PERSPECTIVE, " 2026-09 ",
                " 2026-09-01 ", " 2026-09-30 ", " operator ");

        assertSame(expected, result);
        verify(repository).create("FC-001", PERSPECTIVE, "2026-09", "2026-09-01",
                "2026-09-30", "operator");
    }

    /** 验证 publish 会 trim 查询和发布文本参数，并原样返回 repository.publish 的对象。 */
    @Test
    void publishTrimsTextArgumentsAndReturnsRepositoryResult() {
        FcVersion draft = version("DRAFT", false);
        FcVersion expected = version("PUBLISHED", true);
        when(repository.find("FC-001", PERSPECTIVE, "2026-09")).thenReturn(Optional.of(draft));
        when(repository.publish("FC-001", PERSPECTIVE, "2026-09", "operator"))
                .thenReturn(expected);

        FcVersion result = service.publish(" FC-001 ", PERSPECTIVE, " 2026-09 ", " operator ");

        assertSame(expected, result);
        verify(repository).find("FC-001", PERSPECTIVE, "2026-09");
        verify(repository).publish("FC-001", PERSPECTIVE, "2026-09", "operator");
    }

    /** 验证已发布且 official 的版本发布幂等返回原对象，并不调用 repository.publish。 */
    @Test
    void publishReturnsPublishedOfficialVersionWithoutPublishingAgain() {
        FcVersion existing = version("PUBLISHED", true);
        when(repository.find("FC-001", PERSPECTIVE, "2026-09")).thenReturn(Optional.of(existing));

        FcVersion result = service.publish(" FC-001 ", PERSPECTIVE, " 2026-09 ", " operator ");

        assertSame(existing, result);
        verify(repository).find("FC-001", PERSPECTIVE, "2026-09");
        verify(repository, never()).publish("FC-001", PERSPECTIVE, "2026-09", "operator");
    }

    /** 验证 create 缺少任一必要参数时抛出当前异常，并且 repository 无交互。 */
    @Test
    void createRejectsMissingRequiredArgumentsWithoutRepositoryInteraction() {
        assertMissing("versionCode不能为空", () -> service.create(null, PERSPECTIVE,
                "2026-09", "2026-09-01", "2026-09-30", "operator"));
        assertMissing("basePeriod不能为空", () -> service.create("FC-001", PERSPECTIVE,
                " ", "2026-09-01", "2026-09-30", "operator"));
        assertMissing("coverageStart不能为空", () -> service.create("FC-001", PERSPECTIVE,
                "2026-09", null, "2026-09-30", "operator"));
        assertMissing("coverageEnd不能为空", () -> service.create("FC-001", PERSPECTIVE,
                "2026-09", "2026-09-01", "\t", "operator"));
        assertMissing("operator不能为空", () -> service.create("FC-001", PERSPECTIVE,
                "2026-09", "2026-09-01", "2026-09-30", null));
        assertMissing("perspective不能为空", () -> service.create("FC-001", null,
                "2026-09", "2026-09-01", "2026-09-30", "operator"));

        verifyNoInteractions(repository);
    }

    /** 验证 publish 缺少任一必要参数时抛出当前异常，并且 repository 无交互。 */
    @Test
    void publishRejectsMissingRequiredArgumentsWithoutRepositoryInteraction() {
        assertMissing("versionCode不能为空", () -> service.publish(null, PERSPECTIVE,
                "2026-09", "operator"));
        assertMissing("basePeriod不能为空", () -> service.publish("FC-001", PERSPECTIVE,
                " ", "operator"));
        assertMissing("operator不能为空", () -> service.publish("FC-001", PERSPECTIVE,
                "2026-09", "\t"));
        assertMissing("perspective不能为空", () -> service.publish("FC-001", null,
                "2026-09", "operator"));

        verifyNoInteractions(repository);
    }

    private void assertMissing(String message, org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action);
        assertEquals(message, error.getMessage());
    }

    private static FcVersion version(String status, boolean official) {
        return new FcVersion("FC-001", PERSPECTIVE, "2026-09", "2026-09-01", "2026-09-30",
                status, "uploader", null, official, official ? "publisher" : null, null);
    }
}

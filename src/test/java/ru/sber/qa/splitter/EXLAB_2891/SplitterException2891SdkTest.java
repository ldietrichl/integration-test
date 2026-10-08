package ru.sber.qa.splitter.EXLAB_2891;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;
import steps.sdk.splitter.SplitterExceptionSteps;

import util.validation.ExceptionChainAssertions;

import org.junit.jupiter.api.*;
import ru.sber.qa.allure.Regression;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static steps.sdk.splitter.SplitterSdkAccess.*;

@EnabledIfSystemProperty(named = "exlab2891.sdk.enabled", matches = "true")
@DisplayName("EXLAB-2891. SDK: причины SplitterException")
public class SplitterException2891SdkTest extends SplitterExceptionSteps {
    @Regression
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"T26: исходное исключение без cause", "T27: вложенная причина"})
    @DisplayName("EXLAB-2891-T26/T27. Сохранены причины и место возникновения")
    void originalCauseChain(String variant) throws Throwable {
        verifyUnexpected(variant.startsWith("T26") ? new IllegalStateException("2891-origin")
                : new IllegalStateException("2891-wrapper", new IllegalArgumentException("2891-root")));
    }

    @Regression
    @Test @DisplayName("EXLAB-2891-T28. Готовый SplitterException не теряет код, детали и причину")
    void existingSplitterException() throws Throwable {
        String id = UUID.randomUUID().toString();
        Throwable original = (Throwable) type(ROOT + "exception.SplitterException")
                .getConstructor(String.class, type(ROOT + "num.SplitterExceptionCode"), String.class, String.class)
                .newInstance(id, code("SplitterExceptionCode", "VALIDATION_FAILED"), "validation", "splittingObjects");
        original.initCause(new IllegalArgumentException("2891-origin"));
        Object service = failingService(original);
        var expected = ExceptionChainAssertions.snapshot(original.getCause());
        Throwable actual = assertThrows(Throwable.class, () -> call(service, "calculatePreliminary", request(id)));
        attach(actual);
        assertEquals(type(ROOT + "exception.SplitterException"), actual.getClass());
        assertEquals(id, call(actual, "getRequestId"));
        assertEquals("VALIDATION_FAILED", call(actual, "getErrorCode").toString());
        assertEquals("splittingObjects", call(actual, "getErrorDetails"));
        assertEquals("validation", call(actual, "getErrorMessage"));
        ExceptionChainAssertions.preserves(actual.getCause(), expected);
    }
}

package steps.sdk.splitter;

import util.validation.ExceptionChainAssertions;

import io.qameta.allure.Allure;
import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static steps.sdk.splitter.SplitterSdkAccess.*;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class SplitterExceptionSteps {

    public void verifyUnexpected(Throwable original) throws Throwable {
        String id = UUID.randomUUID().toString();
        Object service = failingService(original);
        var expected = ExceptionChainAssertions.snapshot(original);
        Throwable actual = assertThrows(Throwable.class, () -> call(service, "calculatePreliminary", request(id)));
        attach(actual);
        assertEquals(type(ROOT + "exception.SplitterException"), actual.getClass());
        assertEquals("EXCEPTION", call(actual, "getErrorCode").toString());
        assertEquals(id, call(actual, "getRequestId"));
        ExceptionChainAssertions.preserves(actual.getCause(), expected);
    }

    public static void attach(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        Allure.addAttachment("SplitterException — полная цепочка причин", "text/plain", text.toString(), ".txt");
    }
}

package support.splitter;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;

import io.qameta.allure.*;
import io.qameta.allure.model.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reusable offline fixtures; test cases remain in their ticket package. */
public abstract class Precalc2885ReportingFixtures {

    public static final class Writer implements AllureResultsWriter {
        public final Map<String, String> attachments = new LinkedHashMap<>();
        public TestResult result;
        public TestResultContainer container;
        @Override public void write(TestResult result) { this.result = result; }
        @Override public void write(TestResultContainer container) { this.container = container; }
        @Override public void write(String source, InputStream input) {
            try { attachments.put(source, new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
    }
}

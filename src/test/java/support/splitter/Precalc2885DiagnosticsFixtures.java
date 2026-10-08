package support.splitter;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;

import config.services.splitter.ReactionsPrecalcProfile;

import infrastructure.kubernetes.*;

import io.qameta.allure.*;
import io.qameta.allure.model.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Reusable offline fixtures; test cases remain in their ticket package. */
public abstract class Precalc2885DiagnosticsFixtures {
    public static final String FLAG = "SPLITTER_PRELIMINARY_CALCULATION_ENABLED";
    public static ReactionsPrecalcProfile.Plan plan(String profile, String mode) {
        return ReactionsPrecalcProfile.plan(profile, mode, key -> null, key -> null, key -> null);
    }
    public static final class MemoryWriter implements AllureResultsWriter {
        public final Map<String, String> attachments = new HashMap<>();
        public TestResult result;
        @Override public void write(TestResult result) { this.result = result; }
        @Override public void write(TestResultContainer container) { }
        @Override public void write(String source, InputStream input) {
            try { attachments.put(source, new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
    }
}

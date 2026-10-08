package ru.sber.qa.splitter.EXPLAB_2885;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;
import support.splitter.Precalc2885EvidenceScopeFixtures;

import com.fasterxml.jackson.databind.JsonNode;

import infrastructure.kubernetes.OcServiceLogs;
import io.fabric8.kubernetes.api.model.*;

import io.qameta.allure.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real shared evidence implementation with a mocked Fabric8 transport; no cluster access. */
@Regression @AnyConfigLoadMode @ResourceLock("allure-lifecycle")
public class Precalc2885EvidenceScopeTest extends Precalc2885EvidenceScopeFixtures {
    @Test void successHasOnlyOnePlainTextPodLogInsteadOfRepeatedHistory() throws Exception {
        try (var f = new Fixture()) {
            Instant first = Instant.parse("2026-10-04T17:26:00Z");
            f.begin(first);
            for (int i=0; i<12; i++) f.capture("readiness");
            assertEquals(0, f.writer.attachments.size());
            f.finish(false);
            f.write();
            var attachments = f.writer.result.getAttachments();
            assertEquals(1, attachments.size());
            assertTrue(attachments.get(0).getName().startsWith("Лог pod сервиса"));
            assertEquals("text/plain", attachments.get(0).getType());
            assertFalse(attachments.stream().anyMatch(a -> a.getName().contains("timeline") || a.getName().contains("quota")));
            verify(f.api.pods().inNamespace("test").withName("pod").inContainer("app").usingTimestamps().limitBytes(4096))
                    .sinceTime(first.toString());
        }
    }

    @Test void secondScenarioStartsNewWindowAndFailureAddsEventsWithoutQuotaOrTimeline() throws Exception {
        try (var f = new Fixture()) {
            Instant first = Instant.parse("2026-10-04T17:26:00Z"), next = first.plusSeconds(120);
            f.begin(first); f.capture("readiness"); f.finish(false);
            f.begin(next); f.capture("readiness"); f.finish(true); f.write();
            assertTrue(f.writer.result.getAttachments().stream().anyMatch(a -> a.getName().equals("События pod при ошибке")));
            assertFalse(f.writer.result.getAttachments().stream().anyMatch(a -> a.getName().contains("timeline") || a.getName().contains("quota")));
            verify(f.api.pods().inNamespace("test").withName("pod").inContainer("app").usingTimestamps().limitBytes(4096)).sinceTime(next.toString());
            int previousCount = f.writer.attachments.size();
            f.capture("readiness");
            assertTrue(f.writer.attachments.size() > previousCount, "Default evidence mode is restored after the scope");
        }
    }

    @Test void successfulPodReplacementBuffersLogsWithoutDetailedEvents() throws Exception {
        try (var f = new Fixture()) {
            f.begin(Instant.now().minusSeconds(10)); f.capture("pod-replacement-before-delete"); f.write();
            assertTrue(f.writer.result.getAttachments().isEmpty());
            f.finish(false); f.write();
            assertEquals(1, f.writer.result.getAttachments().size());
            assertTrue(f.writer.result.getAttachments().get(0).getName().startsWith("Лог pod сервиса"));
        }
    }

    @Test void exactOperationWindowExcludesPreparationAndTeardownLines() throws Exception {
        try (var f = new Fixture()) {
            Instant start = Instant.parse("2026-10-04T19:00:00Z"), end = start.plusSeconds(5);
            f.logs("2026-10-04T18:59:59Z before\n2026-10-04T19:00:00Z first\n"
                    + "2026-10-04T19:00:03Z exception\n2026-10-04T19:00:04Z   at example.Stack.run(Stack.java:1)\n"
                    + "2026-10-04T19:00:05Z last\n2026-10-04T19:00:06Z after\n");
            var capture = f.window(start, end);
            assertEquals("COLLECTED", capture.metadata().get("status"));
            assertEquals(2, capture.metadata().get("outsideWindowLines"));
            assertFalse(capture.text().contains("before")); assertFalse(capture.text().contains("after"));
            assertTrue(capture.text().contains("first")); assertTrue(capture.text().contains("last"));
            assertTrue(capture.text().contains("at example.Stack.run"));
            assertEquals(start.toString(), capture.metadata().get("sinceUtc"));
            assertEquals(end.toString(), capture.metadata().get("untilUtc"));
        }
    }

    @Test void deletedPodLogSurvivesUntilTeardown() throws Exception {
        try (var f = new Fixture()) {
            f.begin(Instant.now().minusSeconds(10));
            f.capture("pod-replacement-before-delete");
            f.invoke("finishScenario", new Class<?>[]{String.class,JsonNode.class,List.class,List.class,Instant.class,boolean.class},
                    "after scenario", null, List.of(), List.of(), Instant.now(), false);
            f.write();
            assertEquals(1, f.writer.result.getAttachments().size());
            String text = f.writer.attachments.get(f.writer.result.getAttachments().get(0).getSource());
            assertTrue(text.contains("current operation log"));
            assertTrue(text.contains("podUid=uid"));
        }
    }

    @Test void emptyLogStillHasVisibleTextAttachmentWithStatus() throws Exception {
        try (var f = new Fixture()) {
            f.logs(""); f.begin(Instant.now()); f.finish(false); f.write();
            var attachment = f.writer.result.getAttachments().get(0);
            assertEquals("text/plain", attachment.getType());
            assertTrue(f.writer.attachments.get(attachment.getSource()).contains("status=EMPTY_WINDOW"));
        }
    }

    @Test void unexpectedTimestampIsKeptButMarkedAsUnverified() throws Exception {
        try (var f = new Fixture()) {
            f.logs("server returned an untimestamped line\n");
            var capture = f.window(Instant.now().minusSeconds(2), Instant.now());
            assertEquals("PARTIAL_UNVERIFIED_TIMESTAMPS", capture.metadata().get("status"));
            assertTrue(capture.text().contains("untimestamped line"));
        }
    }

    @Test void logReadFailureIsVisibleAndDoesNotThrowAwayTheTestResult() throws Exception {
        try (var f = new Fixture()) {
            when(f.api.pods().inNamespace("test").withName("pod").inContainer("app").usingTimestamps().limitBytes(4096)
                    .sinceTime(anyString()).tailingLines(2000).getLog()).thenThrow(new IllegalStateException("transport unavailable"));
            f.begin(Instant.now()); f.finish(true); f.write();
            var attachment = f.writer.result.getAttachments().stream().filter(a -> a.getName().startsWith("Лог pod")).findFirst().orElseThrow();
            assertTrue(f.writer.attachments.get(attachment.getSource()).contains("failureType=IllegalStateException"));
        }
    }

    @Test void configMapAttachmentShowsActualManagedValuesAndOmitsUnrelatedKeys() throws Exception {
        var value = dto.splitter.precalc.ReactionsPrecalcRequests.JSON.createObjectNode();
        value.putObject("metadata").put("name", "splitter-map").put("resourceVersion", "123");
        value.putObject("data").put("SPLITTER_PRELIMINARY_CALCULATION_ENABLED", "false")
                .put("splitter-rules-reactions.yml", "rules:\n  rule-code: MAIN\n")
                .put("OTHER_SECRET", "do-not-publish");
        var method = Class.forName("infrastructure.kubernetes.WorkloadEvidence")
                .getDeclaredMethod("configMapText", JsonNode.class, Set.class);
        method.setAccessible(true);
        String text = (String) method.invoke(null, value, Set.of("SPLITTER_PRELIMINARY_CALCULATION_ENABLED", "splitter-rules-reactions.yml", "ABSENT"));
        assertTrue(text.contains("false")); assertTrue(text.contains("rule-code: MAIN"));
        assertTrue(text.contains("resourceVersion=123")); assertTrue(text.contains("[MISSING]"));
        assertFalse(text.contains("OTHER_SECRET")); assertFalse(text.contains("do-not-publish"));
    }

    @Test void serverSideByteLimitIsReportedAsPartial() throws Exception {
        try (var f = new Fixture()) {
            when(f.api.pods().inNamespace("test").withName("pod").inContainer("app").limitBytes(4096)
                    .sinceTime(anyString()).tailingLines(2000).getLog()).thenReturn("x".repeat(4095));
            var log = OcServiceLogs.captureNative(f.settings, f.api, new OcServiceLogs.Target("pod","uid","app",8080,0),
                    "test", Instant.now().minusSeconds(2), Instant.now());
            assertEquals(true, log.metadata().get("mayBeTruncated"));
            assertEquals("PARTIAL_LIMIT", log.metadata().get("status"));
        }
    }
}

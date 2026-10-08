package ru.sber.qa.splitter.EXPLAB_2885;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;
import support.splitter.Precalc2885DiagnosticsFixtures;
import infrastructure.kubernetes.WorkloadScenarioEvidence;

import config.services.splitter.ReactionsPrecalcProfile;
import config.extensions.ReactionsPrecalcBindings;

import config.extensions.WorkloadRunScopeExtension;
import infrastructure.kubernetes.*;
import steps.container.KubernetesWorkloadSteps;
import io.qameta.allure.*;
import io.qameta.allure.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;

import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Offline checks against the actual corporate managed-workload classes, without cluster access. */
@Regression @AnyConfigLoadMode @ResourceLock("allure-lifecycle")
public class Precalc2885DiagnosticsTest extends Precalc2885DiagnosticsFixtures {
    @Test void restEnablesPrecalcAndManualLoad() {
        var p = plan("", "rest");
        assertEquals("true", p.environment().get(FLAG));
        assertEquals("true", p.environment().get("SPLITTER_API_CONFIG_LOAD"));
        assertEquals("splitter-reactions", p.target().workload());
        assertFalse(p.fresh());
    }
    @Test void standardRunsReceiveTheSameWorkloadDefaultsAs2690GradleTasks() {
        var defaults = ReactionsPrecalcBindings.defaults("dev", plan("", "rest"));
        assertEquals("splitter-reactions-service", defaults.get("stand.dev.workloads.splitter-reactions.deployment"));
        assertEquals("splitter-reactions-service-lib", defaults.get("stand.dev.workloads.splitter-reactions.configmap"));
        assertEquals("120", defaults.get("stand.dev.workloads.splitter-reactions.readiness.timeout.seconds"));
    }
    @Test void deploymentOverrideUsesExistingStandBeforeTaskFallback() {
        var properties = Map.of("explab2690.reactions.stand.deployment", "legacy-deployment",
                "explab2885.reactions.stand.deployment", "ticket-deployment");
        var p = ReactionsPrecalcProfile.plan("", "rest", properties::get, key -> null, key -> null);
        assertEquals("ticket-deployment", p.deployment());
        p = ReactionsPrecalcProfile.plan("", "rest", properties::get,
                key -> key.endsWith(".deployment") ? "configured-deployment" : null, key -> null);
        assertEquals("configured-deployment", p.deployment());
    }
    @Test void corporateStandSettingsResolvesInjectedWorkloadNames() {
        var stand = new config.services.core.StandSettings();
        String workload = "selfcheck-" + UUID.randomUUID();
        var p = ReactionsPrecalcProfile.plan("", "rest",
                key -> key.equals("explab2885.reactions.stand.workload") ? workload : null, key -> null, key -> null);
        var defaults = ReactionsPrecalcBindings.defaults(stand.environment, p);
        String prefix = "stand." + stand.environment + ".";
        try {
            ReactionsPrecalcBindings.installMissing(defaults, key -> stand.optional(key.substring(prefix.length()), null));
            assertEquals("splitter-reactions-service", stand.required("workloads." + workload + ".deployment"));
            assertEquals("splitter-reactions-service-lib", stand.required("workloads." + workload + ".configmap"));
        } finally { defaults.keySet().forEach(System::clearProperty); }
    }
    @Test void jvmDefaultsPreserveExplicitAndFileSettingsAcrossPreparations() {
        String prefix = "explab2885.selfcheck." + UUID.randomUUID() + ".";
        String missing = prefix + "missing", explicit = prefix + "explicit", file = prefix + "file";
        System.setProperty(explicit, "original");
        try {
            var defaults = Map.of(missing, "default", explicit, "unused", file, "unused");
            Function<String, String> configured = key -> key.equals(file) ? "corporate-setting" : System.getProperty(key);
            ReactionsPrecalcBindings.installMissing(defaults, configured);
            ReactionsPrecalcBindings.installMissing(defaults, configured);
            assertEquals("default", System.getProperty(missing));
            assertEquals("original", System.getProperty(explicit));
            assertNull(System.getProperty(file));
        } finally { System.clearProperty(missing); System.clearProperty(explicit); System.clearProperty(file); }
    }
    @Test void kafkaDisablesManualLoadOnly() {
        assertEquals("true", plan("", "kafka").environment().get(FLAG));
        assertEquals("false", plan("", "kafka").environment().get("SPLITTER_API_CONFIG_LOAD"));
    }
    @Test void offProfilesExplicitlyDisablePrecalc() {
        for (String p : List.of("OFF", "MONITOR_OFF")) assertEquals("false", plan(p, "rest").environment().get(FLAG));
    }
    @Test void freshProfilesRequireFreshProcess() {
        for (String p : List.of("FRESH", "NO_TABLE", "MONITOR_FRESH", "MONITOR_NO_CONFIG")) assertTrue(plan(p, "rest").fresh());
        assertFalse(plan("RESTART", "rest").fresh());
    }
    @Test void reuses2690TargetsAndAllows2885Override() {
        var props = Map.of("explab2690.reactions.stand.service", "corporate-reactions",
                "explab2690.reactions.stand.workload", "old-workload",
                "explab2885.reactions.stand.workload", "new-workload",
                "explab2690.reactions.application-env.preliminary-calculation-enabled", "CUSTOM_PRECALC");
        var p = ReactionsPrecalcProfile.plan("", "rest", props::get, k -> null, k -> null);
        assertEquals("new-workload", p.target().workload());
        assertEquals("corporate-reactions", p.target().service());
        assertEquals("true", p.environment().get("CUSTOM_PRECALC"));
    }
    @Test void matrixRequiresAllExplicitFlags() {
        assertThrows(IllegalArgumentException.class, () -> plan("MATRIX", "rest"));
        var p = ReactionsPrecalcProfile.plan("MATRIX", "rest", k -> null, k -> null, k -> "false");
        assertEquals("false", p.environment().get("SPLITTER_ALL_RULE_CODE_EXP_ENABLED"));
        assertEquals("true", p.environment().get(FLAG));
    }
    @Test void rejectsInvalidModesProfilesAndConflictingEnvironmentNames() {
        assertThrows(IllegalArgumentException.class, () -> plan("", "typo"));
        assertThrows(IllegalArgumentException.class, () -> plan("RESTART_VERIFY", "rest"));
        assertThrows(IllegalArgumentException.class, () -> ReactionsPrecalcProfile.plan("", "rest",
                k -> k.contains("application-env.") ? "SAME_NAME" : null, k -> null, k -> null));
    }
    @Test void resolvesEnvironmentReferencesOnEveryPreparationAndMergesRules() {
        var p = plan("", "rest");
        var control = mock(KubernetesWorkloadControl.class);
        when(control.environmentState(p.environment())).thenReturn(List.of(new ConfigMapState(p.rulesMap(), Map.of("enabled", "true"))));
        var desired = ReactionsPrecalcProfile.desiredState(p, control);
        assertEquals(1, desired.size());
        assertEquals("true", desired.get(0).data().get("enabled"));
        assertEquals(ReactionsPrecalcProfile.RULES, desired.get(0).data().get(p.rulesKey()));
        ReactionsPrecalcProfile.desiredState(p, control);
        verify(control, times(2)).environmentState(p.environment());
    }
    @Test void conflictsStopBeforeAnyWrite() {
        assertThrows(IllegalStateException.class, () -> ReactionsPrecalcProfile.merge(List.of(
                new ConfigMapState("config", Map.of("key", "true")), new ConfigMapState("config", Map.of("key", "false")))));
    }
    @Test void commonStepsRestartOnlyAfterAConfigurationWrite() {
        var control = mock(KubernetesWorkloadControl.class);
        var steps = new KubernetesWorkloadSteps(target -> control);
        var states = List.of(new ConfigMapState("config", Map.of("enabled", "true")));
        when(control.configurationWrites()).thenReturn(0, 1, 1, 1, 1, 2);
        steps.prepare(control, states, null);
        verify(control).restart();
        steps.ensureState(control, states); // Already enabled: no extra restart.
        verify(control).restart();
        steps.ensureState(control, states); // External drift: new write, new restart.
        verify(control, times(2)).restart();
        verify(control, times(3)).applyState(states);
        verify(control, times(3)).awaitAvailability();
    }
    @Test void preparationFailureClosesSessionAndPreservesPrimaryFailure() {
        var control = mock(KubernetesWorkloadControl.class);
        var failure = new IllegalStateException("apply failed");
        doThrow(failure).when(control).applyState(anyList());
        doThrow(new IllegalStateException("restore failed")).when(control).close();
        var steps = new KubernetesWorkloadSteps(target -> control);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> steps.prepare(control, List.of(), null)));
        assertEquals(1, failure.getSuppressed().length);
        verify(control, never()).restart();
    }
    @Test void runScopeRechecksEveryScenarioAndRestoresAtRunEnd() throws Throwable {
        var context = mock(ExtensionContext.class);
        var store = mock(ExtensionContext.Store.class);
        Map<Object, Object> values = new HashMap<>();
        when(context.getRoot()).thenReturn(context);
        when(context.getStore(any())).thenReturn(store);
        when(store.getOrComputeIfAbsent(any(), any(), any())).thenAnswer(call -> values.computeIfAbsent(call.getArgument(0),
                key -> ((Function<Object, Object>) call.getArgument(1)).apply(key)));
        var control = mock(KubernetesWorkloadControl.class);
        when(control.isRestored()).thenReturn(true);
        var steps = new KubernetesWorkloadSteps(target -> control);
        var extension = new WorkloadRunScopeExtension();
        var p = plan("", "rest");
        when(control.environmentState(anyMap())).thenReturn(List.of(new ConfigMapState("config", Map.of("enabled", "true"))));
        try {
            for (int n = 0; n < 2; n++) {
                extension.beforeEach(context);
                WorkloadRunScopeExtension.ensure(p.target(), c -> ReactionsPrecalcProfile.desiredState(p, c), steps, null);
                extension.afterEach(context);
            }
            verify(control, times(2)).environmentState(p.environment());
            verify(control, times(2)).applyState(anyList());
            verify(control, never()).close();
        } finally {
            extension.afterEach(context);
            for (Object resource : values.values()) ((ExtensionContext.Store.CloseableResource) resource).close();
        }
        verify(control).close();
    }
    @Test void restartUsesCommonControlAndWaitsForReadiness() {
        var control = mock(KubernetesWorkloadControl.class);
        var steps = mock(KubernetesWorkloadSteps.class);
        new ReactionsPrecalcProfile(steps, control).restart();
        var order = inOrder(control, steps);
        order.verify(control).captureEvidence(anyString());
        order.verify(steps).replacePod(control);
        order.verify(steps).checkReady(control);
        order.verify(steps).checkAvailability(control);
    }
    @Test void attachesScenarioJournalEvenIfCommonEvidenceFails() {
        var previous = Allure.getLifecycle();
        var writer = new MemoryWriter();
        var lifecycle = new AllureLifecycle(writer);
        Allure.setLifecycle(lifecycle);
        String id = UUID.randomUUID().toString();
        try {
            lifecycle.scheduleTestCase(new TestResult().setUuid(id).setName("evidence"));
            lifecycle.startTestCase(id);
            var control = mock(KubernetesWorkloadControl.class);
            doThrow(new IllegalStateException("do not attach credentials")).when(control).finishScenarioEvidence(anyString(), anyBoolean());
            var evidence = new WorkloadScenarioEvidence("EXPLAB-2885", "scenario");
            evidence.bind(control);
            evidence.failed(new AssertionError("business failure"));
            assertDoesNotThrow(evidence::finish);
            lifecycle.stopTestCase(id);
            lifecycle.writeTestCase(id);
            assertEquals(1, writer.result.getAttachments().size());
            String all = String.join("\n", writer.attachments.values());
            assertTrue(all.contains("PREPARATION_FAILED"));
            assertTrue(all.contains("COMMON_EVIDENCE_UNAVAILABLE"));
            assertFalse(all.contains("do not attach credentials"));
            verify(control).beginScenarioEvidence(any());
            verify(control).finishScenarioEvidence(anyString(), eq(true));
        } finally { Allure.setLifecycle(previous); }
    }
}

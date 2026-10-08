package infrastructure.kubernetes;

import config.extensions.WorkloadRunScopeExtension;
import config.extensions.WorkloadScenarioEvidenceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import steps.container.KubernetesWorkloadSteps;
import steps.container.WorkloadScenarioSteps;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkloadArchitectureTest {
    @Test void anotherServiceRechecksConfigurationOnEveryScenarioThroughSharedSteps() {
        var target = new WorkloadTarget("billing", "billing-service", 8080, "billing-app");
        var control = mock(KubernetesWorkloadControl.class);
        var steps = mock(KubernetesWorkloadSteps.class);
        var scenario = new WorkloadScenarioSteps(steps);
        var evidence = mock(WorkloadScenarioEvidence.class);
        var probe = mock(WorkloadAvailabilityProbe.class);
        var reads = new AtomicInteger();
        try (var scope = mockStatic(WorkloadRunScopeExtension.class)) {
            scope.when(() -> WorkloadRunScopeExtension.ensure(eq(target), any(Function.class), eq(steps), eq(probe)))
                    .thenAnswer(call -> {
                        Function<KubernetesWorkloadControl, List<ConfigMapState>> state = call.getArgument(1);
                        var merged = state.apply(control);
                        assertEquals(1, merged.size());
                        assertEquals(Map.of("enabled", "true", "mode", "sync"), merged.get(0).data());
                        return null;
                    });
            for (int i = 0; i < 2; i++) {
                assertSame(control, scenario.prepare(target, session -> {
                    assertSame(control, session);
                    reads.incrementAndGet();
                    return List.of(new ConfigMapState("billing-config", Map.of("enabled", "true")),
                            new ConfigMapState("billing-config", Map.of("mode", "sync")));
                }, probe, evidence, false));
            }
            assertEquals(2, reads.get());
            verify(evidence, times(2)).bind(control);
            verify(evidence, times(2)).prepared();
            verify(steps, never()).replacePod(any());
        }
    }

    @Test void conflictingConfigurationCannotReachApplyState() {
        assertThrows(IllegalStateException.class, () -> WorkloadStates.merge(List.of(
                new ConfigMapState("billing", Map.of("enabled", "true")),
                new ConfigMapState("billing", Map.of("enabled", "false")))));
        assertEquals(1, WorkloadStates.merge(List.of(
                new ConfigMapState("billing", Map.of("enabled", "true")),
                new ConfigMapState("billing", Map.of("enabled", "true")))).size());
    }

    @Test void restartCapturesOldPodBeforeReplacementAndWaitsForApplication() {
        var control = mock(KubernetesWorkloadControl.class);
        var steps = mock(KubernetesWorkloadSteps.class);
        new WorkloadScenarioSteps(steps).restart(control);
        var order = inOrder(control, steps);
        order.verify(control).captureEvidence("before pod replacement");
        order.verify(steps).replacePod(control);
        order.verify(steps).checkReady(control);
        order.verify(steps).checkAvailability(control);
    }

    @Test void failureSurvivesUnavailableDiagnosticsAndScopeIsResetForNextInvocation() {
        var context = mock(ExtensionContext.class);
        doReturn(WorkloadArchitectureTest.class).when(context).getRequiredTestClass();
        when(context.getDisplayName()).thenReturn("billing operation");
        when(context.getExecutionException()).thenReturn(Optional.of(new AssertionError("business failure")));
        var extension = new WorkloadScenarioEvidenceExtension();
        var control = mock(KubernetesWorkloadControl.class);
        doThrow(new IllegalStateException("pod unavailable")).when(control).finishScenarioOperation(any());
        extension.beforeEach(context);
        var first = WorkloadScenarioEvidence.current();
        first.bind(control);
        extension.beforeTestExecution(context);
        assertDoesNotThrow(() -> extension.afterTestExecution(context));
        assertDoesNotThrow(() -> extension.afterEach(context));
        assertNull(WorkloadScenarioEvidence.currentOrNull());
        verify(control).finishScenarioEvidence("WorkloadArchitectureTest after scenario", true);
        extension.beforeEach(context);
        assertNotSame(first, WorkloadScenarioEvidence.current());
        extension.afterEach(context);
        assertNull(WorkloadScenarioEvidence.currentOrNull());
    }

    @Test void teardownAttachesEvidenceOnlyOnceEvenWithExtensionFallback() {
        var control = mock(KubernetesWorkloadControl.class);
        var evidence = new WorkloadScenarioEvidence("billing", "normal operation");
        evidence.bind(control);
        evidence.finish();
        evidence.finish();
        verify(control, times(1)).finishScenarioEvidence("billing after scenario", false);
    }
}

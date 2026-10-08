package ru.sber.qa.splitter.EXPLAB_2885;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;
import support.splitter.Precalc2885ReportingFixtures;
import infrastructure.kubernetes.WorkloadScenarioEvidence;
import steps.reporting.ReportingSteps;
import steps.flow.splitter.reactions.ReactionsPrecalcSteps;

import infrastructure.kubernetes.KubernetesWorkloadControl;
import io.qameta.allure.*;
import io.qameta.allure.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Regression @AnyConfigLoadMode @ResourceLock("allure-lifecycle")
public class Precalc2885ReportingTest extends Precalc2885ReportingFixtures {
    @Test void fixturesKeepDiagnosticsOutsideBusinessStepsAndPreserveAssertionFailure() throws Exception {
        var previous = Allure.getLifecycle();
        var writer = new Writer();
        var lifecycle = new AllureLifecycle(writer);
        Allure.setLifecycle(lifecycle);
        String container = UUID.randomUUID().toString(), test = UUID.randomUUID().toString();
        try {
            lifecycle.startTestContainer(new TestResultContainer().setUuid(container));
            lifecycle.scheduleTestCase(container, new TestResult().setUuid(test).setName("report structure"));
            lifecycle.startPrepareFixture(container, "before", new FixtureResult().setName("prepareExplab2885StandState"));
            var diagnostics = new WorkloadScenarioEvidence("EXPLAB-2885", "test");
            var control = mock(KubernetesWorkloadControl.class);
            ReportingSteps.step("Проверить состояние стенда", () -> diagnostics.bind(control));
            diagnostics.prepared();
            lifecycle.stopFixture("before");
            lifecycle.startTestCase(test);
            ReportingSteps.step("Выполнить предрасчёт", () -> Allure.addAttachment("request", "{}"));
            assertThrows(AssertionError.class, () -> util.splittercheck.ReactionsPrecalcAssertions.assertMainA(
                    dto.splitter.precalc.ReactionsPrecalcRequests.JSON.createObjectNode().set("splittingResults", dto.splitter.precalc.ReactionsPrecalcRequests.JSON.createArrayNode()), 101));
            lifecycle.stopTestCase(test);
            lifecycle.startTearDownFixture(container, "after", new FixtureResult().setName("collectExplab2885Diagnostics"));
            diagnostics.failed(new AssertionError("original assertion"));
            diagnostics.finish();
            lifecycle.stopFixture("after");
            lifecycle.writeTestCase(test);
            lifecycle.stopTestContainer(container); lifecycle.writeTestContainer(container);
            assertEquals(2, writer.result.getSteps().size());
            assertEquals(Status.PASSED, writer.result.getSteps().get(0).getStatus());
            assertEquals(Status.FAILED, writer.result.getSteps().get(1).getStatus());
            assertTrue(writer.result.getSteps().get(1).getName().startsWith("Проверить MAIN"));
            assertTrue(writer.result.getAttachments().isEmpty());
            assertEquals(1, writer.container.getBefores().size());
            assertEquals(1, writer.container.getAfters().size());
            assertEquals(1, writer.container.getAfters().get(0).getAttachments().size());
            verify(control).finishScenarioEvidence("EXPLAB-2885 after scenario", true);
            assertNotNull(ReactionsPrecalcSteps.class.getDeclaredMethod("prepareExplab2885StandState")
                    .getAnnotation(org.junit.jupiter.api.BeforeEach.class));
            assertNotNull(ReactionsPrecalcSteps.class.getDeclaredMethod("collectExplab2885Diagnostics")
                    .getAnnotation(org.junit.jupiter.api.AfterEach.class));
        } finally { Allure.setLifecycle(previous); }
    }

    @Test void runtimeMetadataUsesReactionsAndPlainText() {
        var previous = Allure.getLifecycle();
        var writer = new Writer();
        var lifecycle = new AllureLifecycle(writer);
        String id = UUID.randomUUID().toString();
        try (var metadata = mockStatic(util.support.SplitterRuntimeMetadata.class)) {
            metadata.when(() -> util.support.SplitterRuntimeMetadata.summary("REACTIONS")).thenReturn("splitter.splittingPoint=REACTIONS");
            Allure.setLifecycle(lifecycle);
            lifecycle.scheduleTestCase(new TestResult().setUuid(id).setName("runtime metadata")); lifecycle.startTestCase(id);
            var support = mock(ReactionsPrecalcSteps.class, CALLS_REAL_METHODS);
            support.writeSplitterRuntimeMetadataToAllure();
            lifecycle.stopTestCase(id); lifecycle.writeTestCase(id);
            var attachment = writer.result.getAttachments().get(0);
            assertEquals("Splitter runtime metadata", attachment.getName());
            assertEquals("text/plain", attachment.getType());
            assertEquals("splitter.splittingPoint=REACTIONS", writer.attachments.get(attachment.getSource()));
            metadata.verify(() -> util.support.SplitterRuntimeMetadata.summary("REACTIONS"));
        } finally { Allure.setLifecycle(previous); }
    }

    @Test void specialProfilesAreDisabledBeforeLifecycleCanMutateTheStand() {
        for (Class<?> type : List.of(SplitterReactionsPrecalc2885ProfileFlowTest.class,
                SplitterReactionsPrecalc2885MonitoringFlowTest.class)) {
            for (var method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Test.class)) continue;
                var condition = method.getAnnotation(org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable.class);
                assertNotNull(condition, method.getName());
                assertEquals("EXPLAB_2885_PROFILE", condition.named());
                assertFalse(condition.matches().isBlank());
            }
        }
    }
}

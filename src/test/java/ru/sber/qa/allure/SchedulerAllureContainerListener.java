package ru.sber.qa.allure;

import infrastructure.scheduler.SchedulerAllurePresentation;
import io.qameta.allure.listener.ContainerLifecycleListener;
import io.qameta.allure.listener.TestLifecycleListener;
import io.qameta.allure.model.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Mutates only actual scheduler model objects, never global current-test context. */
public final class SchedulerAllureContainerListener implements TestLifecycleListener, ContainerLifecycleListener {
    private static final Set<String> SCHEDULER_IDS = ConcurrentHashMap.newKeySet();
    private static boolean scheduler(TestResult result) {
        return result.getFullName() != null && result.getFullName().startsWith("ru.sber.qa.scheduler.");
    }
    @Override public void beforeTestSchedule(TestResult result) {
        if (scheduler(result) && result.getUuid() != null) SCHEDULER_IDS.add(result.getUuid());
    }
    @Override public void beforeTestWrite(TestResult result) {
        beforeTestSchedule(result);
        if (!scheduler(result)) return;
        result.getLabels().removeIf(label -> "testFramework".equals(label.getName()));
        result.getLabels().add(new Label().setName("testFramework").setValue("platform-v-at-framework"));
        SchedulerAllurePresentation.steps(result.getSteps());
    }
    @Override public void beforeContainerWrite(TestResultContainer container) {
        if (container.getChildren().stream().noneMatch(SCHEDULER_IDS::contains)) return;
        for (FixtureResult fixture : container.getBefores()) translate(fixture);
        for (FixtureResult fixture : container.getAfters()) translate(fixture);
    }
    private static void translate(FixtureResult fixture) {
        fixture.setName(SchedulerAllurePresentation.translate(fixture.getName()));
        SchedulerAllurePresentation.steps(fixture.getSteps());
    }
}

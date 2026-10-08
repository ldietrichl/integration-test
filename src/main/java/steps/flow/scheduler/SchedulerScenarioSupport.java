package steps.flow.scheduler;

import config.services.core.SchedulerSettings;
import flow.Flows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.function.Executable;
import request.scheduler.SchedulerTestDataFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;

/** Pilot-style lifecycle: explicit test flows and cleanup limited to registered, owned data. */
public abstract class SchedulerScenarioSupport extends Flows {
    private final SchedulerSettings schedulerSettings = new SchedulerSettings();
    private static final ThreadLocal<List<SchedulerTestDataFactory>> OWNED = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<infrastructure.kubernetes.SchedulerRegressionSession> SESSION = new ThreadLocal<>();

    protected SchedulerSettings settings() {
        return schedulerSettings;
    }

    public void startSchedulerScenario(TestInfo testInfo) {
        // TestResult labels are written by RequiredAllureLabelsExtension, not against a fixture UUID.
        String scenario = testInfo.getTestMethod()
                .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName())
                .orElse("scheduler-regression");
        if (!OWNED.get().isEmpty() || SESSION.get() != null) throw new IllegalStateException("Previous scheduler lifecycle was not closed");
        SESSION.set(infrastructure.kubernetes.SchedulerRegressionSession.begin(scenario));
    }

    protected void prepareFixture(FlowWithDbRest flow, SchedulerTestDataFactory data, int scenarioId) {
        flow.dbCustomSteps().schedulerBaselineSteps().requireSchema(settings());
        settings().requireFixturePermission();
        if (!flow.dbCustomSteps().schedulerSteps().query(
                "SELECT id FROM scheduler.task_action WHERE object_id IN (?,?)",
                data.objectId, data.objectId + 1).isEmpty()) {
            throw new IllegalStateException("Fixture object namespace collision; retry with a new run");
        }
        // Register before the first write, including negative requests accepted by a defective service.
        infrastructure.scheduler.SchedulerFixtureLedger.register(data.owner, data.objectId, settings().environment);
        OWNED.get().add(data);
    }

    protected long seedTask(FlowWithDbRest flow, SchedulerTestDataFactory data, Integer version, String status) {
        return seedTask(flow, data, version, status, data.objectId,
                "EXP", "START", settings().user(), data.future);
    }

    protected long seedTask(FlowWithDbRest flow, SchedulerTestDataFactory data, Integer version, String status,
                            long objectId, String objectType, String action, long user, long date) {
        return flow.dbCustomSteps().schedulerSteps().seed(
                data.nextFixtureName(), version, status, objectId, objectType, action,
                user, date, settings().splittingPoint());
    }

    public void cleanupSchedulerFixtures() {
        var infrastructureSession = SESSION.get();
        List<Executable> cleanup = new ArrayList<>();
        if (infrastructureSession != null) cleanup.add(infrastructureSession::attachScenarioLogs);
        if (!OWNED.get().isEmpty()) {
            cleanup.add(() -> {
                try {
                    if (settings().managedFixturesEnabled()) SchedulerFixtureStandSteps.requireCleanupReady();
                    var db = new flow.SchedulerInfrastructureFlow().dbCustomSteps().schedulerSteps();
                    io.qameta.allure.Allure.step("Remove only scheduler data registered by this test", () ->
                            assertAll("Scheduler fixture cleanup", OWNED.get().stream()
                                    .map(data -> (Executable) () -> db.cleanup(data.owner, data.objectId))));
                } catch (RuntimeException | Error failure) {
                    SchedulerFixtureStandSteps.fixtureCleanupFailed();
                    throw failure;
                }
            });
        }
        if (infrastructureSession != null) cleanup.add(infrastructureSession::close);
        try {
            // Still close owned tunnels if fixture cleanup fails; retain both failures.
            assertAll("Scheduler scenario cleanup", cleanup);
        } finally {
            OWNED.get().clear();
            SESSION.remove();
            OWNED.remove();
            infrastructure.scheduler.SchedulerFixtureLedger.clear();
        }
    }
}

package steps.flow.scheduler;

import flow.SchedulerInfrastructureFlow;
import config.services.core.SchedulerSettings;
import request.scheduler.SchedulerTestDataFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Owned DB witness only; this does not claim in-flight takeover coverage (SCH-124). */
public final class SchedulerWorkloadRegressionSteps {
    public void pod001() {
        SchedulerSettings settings = new SchedulerSettings();
        settings.requireFixturePermission();
        var flow = new SchedulerInfrastructureFlow();
        var db = flow.dbCustomSteps().schedulerSteps();
        var data = new SchedulerTestDataFactory(settings);
        assertTrue(db.query("SELECT id FROM scheduler.task_action WHERE object_id IN (?,?)",
                data.objectId, data.objectId + 1).isEmpty(), "Fresh owned object namespace required");
        infrastructure.scheduler.SchedulerFixtureLedger.register(data.owner, data.objectId, settings.environment);
        Throwable primary = null;
        try {
            db.seed(data.nextFixtureName(), 1, "PLANNED", data.objectId, "EXP", "START",
                    settings.user(), data.future, settings.splittingPoint());
            var before = db.tasks(data.owner);
            assertEquals(1, before.size(), "An owned task/action witness must exist before pod deletion");
            var health = new SchedulerWorkloadDiagnosticSteps();
            var workload = flow.infrastructureSteps().workloadSteps();
            var control = workload.session("scheduler");
            health.withRestoration(control, "persistence", () -> {
                assertFalse(workload.replacePod(control).isEmpty());
                health.assertHealthy("persistence-after-pod-replacement");
                assertEquals(before, db.tasks(data.owner), "Owned task and action must survive pod replacement");
            });
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            infrastructure.scheduler.SchedulerCleanupFailures.run(primary, () -> {
                try {
                    if (settings.managedFixturesEnabled()) SchedulerFixtureStandSteps.requireCleanupReady();
                    db.cleanup(data.owner, data.objectId);
                } catch (RuntimeException | Error cleanup) {
                    SchedulerFixtureStandSteps.fixtureCleanupFailed();
                    throw cleanup;
                }
            });
        }
    }
}

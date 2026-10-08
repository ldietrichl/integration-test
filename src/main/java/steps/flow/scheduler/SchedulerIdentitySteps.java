package steps.flow.scheduler;

import config.services.core.SchedulerApplicationIdentity;
import config.services.core.StandFixtureUsers;
import flow.Flows;
import infrastructure.kubernetes.SchedulerRegressionSession;
import static steps.rest.scheduler.SchedulerSteps.expect;

/** Separate DEV effective-actor observation from authenticated user-service resolution. */
public final class SchedulerIdentitySteps extends Flows {
    public record DevActor(long taskId, long authorId) { }

    /** Called only after the scenario has registered ownership and paused scheduler jobs. */
    public static DevActor createDevAuthorWitness(
            steps.rest.scheduler.SchedulerSteps api,
            steps.db.scheduler.SchedulerDbSteps db,
            request.scheduler.SchedulerTestDataFactory data) {
        if (!SchedulerApplicationIdentity.observesDevAuthor())
            throw new IllegalStateException("The anonymous creator probe is approved for DEV only");
        long taskId = util.scheduler.SchedulerAssertions.id(
                expect(api.createV2WithoutIdentity(data.createTaskV2()), 200));
        var owned = db.tasks(data.owner);
        org.junit.jupiter.api.Assertions.assertEquals(1, owned.size(),
                "One owned API task/action must exist before inferring its DEV author");
        var row = owned.get(0);
        org.junit.jupiter.api.Assertions.assertEquals(taskId,
                util.scheduler.SchedulerAssertions.number(row.get("id")), "API/DB task correlation");
        org.junit.jupiter.api.Assertions.assertEquals(data.objectId,
                util.scheduler.SchedulerAssertions.number(row.get("object_id")), "Owned object correlation");
        long author = util.scheduler.SchedulerAssertions.number(row.get("created_by"));
        org.junit.jupiter.api.Assertions.assertTrue(author > 0, "DEV effective author must be persisted");
        steps.container.KubernetesTunnelSteps.evidence("DEV effective actor from owned creation", java.util.Map.of(
                "mode", "observed-creator", "taskId", taskId, "createdBy", author,
                "source", "Anonymous create API followed by owned DB row",
                "boundary", "DEV effective actor only; no human authentication or JWT coverage"));
        System.out.println("[scheduler-identity] DEV observed-creator: taskId=" + taskId
                + ", createdBy=" + author + "; not an authenticated human identity");
        return new DevActor(taskId, author);
    }

    public void prepareFixtureUsers() {
        String token = SchedulerApplicationIdentity.requireToken();
        String sub = SchedulerApplicationIdentity.subject(token);
        long[] primary = new long[1];
        StandFixtureUsers.Pair[] users = new StandFixtureUsers.Pair[1];
        try (SchedulerRegressionSession session = SchedulerRegressionSession.begin("MY_TASKS identity preflight")) {
            if (session == null)
                throw new IllegalStateException("MY_TASKS identity lookup requires the configured owned stand tunnels");
            getFlowWithDbRest()
                    .step("Resolve the application caller through the real user-service audit API", flow -> {
                        var response = expect(flow.restCustomSteps().schedulerIdentityUserSteps().getAuditBySub(sub), 200);
                        if (!response.path("id").isIntegralNumber() || !response.path("id").canConvertToLong()
                                || response.path("id").asLong() <= 0)
                            throw new IllegalStateException("User audit API did not return a positive integral id");
                        primary[0] = response.path("id").asLong();
                    })
                    .step("Select the resolved active DB user and a distinct existing control user", flow ->
                            users[0] = flow.dbCustomSteps().standUserFixtureSteps().selectActiveUsersForPrimary(primary[0]))
                    .run();
        }
        StandFixtureUsers.install(users[0]);
        SchedulerApplicationIdentity.bindUser(primary[0]);
    }
}

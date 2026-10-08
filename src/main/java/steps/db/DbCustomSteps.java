package steps.db;

import ru.sber.qa.services.db.DatabaseClient;
import steps.db.configurations.v2.ConfigsDbSteps;
import steps.db.experiments.v2.StatusChangeDbSteps;

public class DbCustomSteps {
    public steps.db.fixtures.StandUserFixtureSteps standUserFixtureSteps() {
        return new steps.db.fixtures.StandUserFixtureSteps(client);
    }
    public steps.db.scheduler.SchedulerDbSteps schedulerSteps() {
        return new steps.db.scheduler.SchedulerDbSteps(client);
    }
    public steps.db.scheduler.SchedulerBaselineSteps schedulerBaselineSteps() {
        return new steps.db.scheduler.SchedulerBaselineSteps(schedulerSteps());
    }

    DatabaseClient client;

    public DbCustomSteps(DatabaseClient client) {
        this.client = client;
    }

    public ConfigsDbSteps configsDbSteps() {
        return new ConfigsDbSteps(client);
    }

    public StatusChangeDbSteps statusChangeDbSteps() {
        return new StatusChangeDbSteps(client);
    }
}

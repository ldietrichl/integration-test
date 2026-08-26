package steps.db;

import ru.sber.qa.services.db.DatabaseClient;
import steps.db.configurations.v2.ConfigsDbSteps;
import steps.db.experiments.v2.StatusChangeDbSteps;

public class DbCustomSteps {
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

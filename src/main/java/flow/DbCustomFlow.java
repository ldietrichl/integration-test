package flow;

import config.environment.special.EnvironmentSpecialConfigWithDb;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.db.DatabaseClient;
import ru.sber.qa.services.db.DatabaseService;
import steps.db.DbCustomSteps;

import java.util.function.Function;

public interface DbCustomFlow {
    default DbCustomSteps dbCustomSteps() {
        return new DbCustomSteps(
                Environment.getForCurrentThread().getService(DatabaseService.class).dataBaseClient("explab")
        );
    }

    default DatabaseClient dbExpLabClient() {
        return Environment.getForCurrentThread().getService(DatabaseService.class).dataBaseClient("explab");
    }

    default <R> R dbExpLabManuallyStartClient(Function<DatabaseClient, R> dbRequest) {
        Environment env = Environment.createForCurrentThread(new EnvironmentSpecialConfigWithDb()).init();
        env.beforeTest();
        DatabaseClient dbClient = env.getService(DatabaseService.class).dataBaseClient("explab");
        R dbResponse = dbRequest.apply(dbClient);
        env.afterTest();
        env.shutdown();

        return dbResponse;
    }
}

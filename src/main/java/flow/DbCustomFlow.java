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
        Environment env = Environment.createForCurrentThread(new EnvironmentSpecialConfigWithDb());
        boolean beforeTestCompleted = false;
        Throwable primary = null;
        try {
            env.init();
            env.beforeTest();
            beforeTestCompleted = true;
            return dbRequest.apply(env.getService(DatabaseService.class).dataBaseClient("explab"));
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = null;
            try {
                if (beforeTestCompleted) env.afterTest();
            } catch (RuntimeException | Error failure) {
                cleanup = failure;
            }
            try {
                env.shutdown();
            } catch (RuntimeException | Error failure) {
                if (cleanup == null) cleanup = failure;
                else if (failure != cleanup) cleanup.addSuppressed(failure);
            }
            if (cleanup != null) {
                if (primary != null) {
                    if (primary != cleanup) primary.addSuppressed(cleanup);
                } else if (cleanup instanceof RuntimeException failure) {
                    throw failure;
                } else {
                    throw (Error) cleanup;
                }
            }
        }
    }
}

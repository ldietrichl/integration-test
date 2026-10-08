package config.services.db;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.db.DataBaseClientWithIndividualConnection;
import ru.sber.qa.services.db.DatabaseClient;
import ru.sber.qa.services.db.DatabaseClientConfiguration;
import ru.sber.qa.services.db.DefaultDatabaseServiceConfiguration;
import java.util.Properties;

public class CustomDatabaseServiceConfiguration extends DefaultDatabaseServiceConfiguration {

    @Override
    public DatabaseClient getDatabaseClient(Environment environment, String databaseName) {
        return new DatabaseClient(createDatabaseClientConfiguration(environment, databaseName));
    }

    @Override
    public DataBaseClientWithIndividualConnection getDatabaseClientWithIndividualConnection(Environment environment, String databaseName) {
        return new DataBaseClientWithIndividualConnection(createDatabaseClientConfiguration(environment, databaseName));
    }

    @Override
    protected DatabaseClientConfiguration createDatabaseClientConfiguration(Environment environment, String databaseName) {
        ConfigurationService configurationService = environment.getService(ConfigurationService.class);
        CustomDatabaseConfigScope customDatabaseConfigScope = new CustomDatabaseConfigScope(databaseName);

        Properties properties = configurationService.getProperties(customDatabaseConfigScope);

        return databaseClientConfigurationFromProperties(databaseName, properties);
    }
}

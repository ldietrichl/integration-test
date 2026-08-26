package config.services.db;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.db.DataBaseClientWithIndividualConnection;
import ru.sber.qa.services.db.DatabaseClient;
import ru.sber.qa.services.db.DatabaseClientConfiguration;
import ru.sber.qa.services.db.DefaultDatabaseServiceConfiguration;
import ru.sber.qa.services.db.exceptions.IncorrectDataBaseClientConfiguration;

import java.time.Duration;
import java.util.Properties;

import static io.perfeccionista.framework.utils.StringUtils.isBlank;
import static ru.sber.qa.services.db.DatabaseClientConfiguration.dataBaseClientConfiguration;

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

        String urlProperty = properties.getProperty("url");
        if (isBlank(urlProperty)) {
            throw IncorrectDataBaseClientConfiguration.exception("Required credentials for the database '" + databaseName + "' are not specified. 'url' is empty");
        }
        String loginProperty = properties.getProperty("login");
        if (isBlank(loginProperty)) {
            throw IncorrectDataBaseClientConfiguration.exception("Required credentials for the database '" + databaseName + "' are not specified. 'login' is empty");
        }
        String passwordProperty = properties.getProperty("password");
        if (isBlank(passwordProperty)) {
            throw IncorrectDataBaseClientConfiguration.exception("Required credentials for the database '" + databaseName + "' are not specified. 'password' is empty");
        }
        String timeoutProperty = properties.getProperty("timeout.in.seconds");
        Duration timeout;
        if (isBlank(timeoutProperty)) {
            timeout = Duration.ofSeconds(60);
        } else {
            timeout = Duration.ofSeconds(Long.parseLong(timeoutProperty));
        }

        return dataBaseClientConfiguration(databaseName, urlProperty, loginProperty, passwordProperty, timeout);
    }
}

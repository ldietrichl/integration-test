package config.services.db;

import config.services.core.TestConfigurationFiles;
import config.services.core.TestEnvironment;
import ru.sber.qa.services.configuration.scope.ConfigScope;

import java.util.Properties;

public class CustomDatabaseConfigScope implements ConfigScope {

    private final String databaseName;

    public CustomDatabaseConfigScope(String databaseName) {
        this.databaseName = databaseName;
    }

    @Override
    public Properties getProperties() {
        return selectedProperties(TestConfigurationFiles.load("database.properties"), TestEnvironment.current(), databaseName);
    }

    static Properties selectedProperties(Properties source, String environment, String databaseName) {
        String prefix = "db." + TestEnvironment.normalize(environment) + "." + databaseName + ".";
        Properties selected = new Properties();
        for (String key : new String[]{"url", "login", "password", "timeout.in.seconds", "connection.pool.size"}) {
            String value = source.getProperty(prefix + key);
            if (value != null) selected.setProperty(key, value);
        }
        return selected;
    }
}

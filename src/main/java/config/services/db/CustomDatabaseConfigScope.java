package config.services.db;

import org.aeonbits.owner.ConfigFactory;
import ru.sber.qa.services.configuration.scope.ConfigScope;

import java.util.Map;
import java.util.Properties;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;

public class CustomDatabaseConfigScope implements ConfigScope {

    private final String databaseName;

    public CustomDatabaseConfigScope(String databaseName) {
        this.databaseName = databaseName;
    }

    @Override
    public Properties getProperties() {
        Properties properties = new Properties();
        CustomDatabaseConfig config = ConfigFactory.create(
                CustomDatabaseConfig.class
                , Map.of(
                        "name", databaseName,
                        "env", dbEnv(TEST_CONFIG.env())));
        properties.put("url", valueOrEmpty(config.url()));
        properties.put("login", valueOrEmpty(config.login()));
        properties.put("password", valueOrEmpty(config.password()));
        properties.put("timeout.in.seconds", valueOrEmpty(config.timeoutInSeconds()));
        return properties;
    }

    private String dbEnv(String env) {
        if ("ift-dm".equals(env) || "eift-dm".equals(env) || "eift".equals(env)) {
            return "ift";
        }
        return env;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}

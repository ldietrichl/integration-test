package config.services.core;

import config.services.db.CustomDatabaseConfig;
import java.util.Map;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseEnvironmentSelectionTest {
    @Test
    @ResourceLock("SYSTEM_PROPERTIES")
    void databasePlaceholderUsesSelectedEnvironmentInsteadOfStaleJvmEnv() {
        String previous = System.getProperty("env");
        try {
            System.setProperty("env", "dev");
            CustomDatabaseConfig config = ConfigFactory.create(CustomDatabaseConfig.class, Map.of(
                    "selectedTestEnvironment", "ift", "name", "synthetic_environment_check",
                    "db.ift.synthetic_environment_check.url", "jdbc:postgresql://ift.example.invalid/test",
                    "db.dev.synthetic_environment_check.url", "jdbc:postgresql://dev.example.invalid/test"));
            assertEquals("jdbc:postgresql://ift.example.invalid/test", config.url());
        } finally {
            if (previous == null) System.clearProperty("env");
            else System.setProperty("env", previous);
        }
    }
}

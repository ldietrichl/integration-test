package config.services.db;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseProfileConfigurationTest {
    @Test
    void selectsOnlyTheRequestedServiceOfTheSelectedEnvironment() {
        Properties source = properties(Map.of(
                "db.dev.experiment.url", "jdbc:postgresql://dev.example.invalid/experiment",
                "db.dev.experiment.password", "${SECURE_DEV_EXPERIMENT_PASSWORD}",
                "db.dev.configuration.url", "jdbc:postgresql://dev.example.invalid/configuration",
                "db.ift.experiment.url", "jdbc:postgresql://ift.example.invalid/experiment",
                "db.ift.experiment.password", "${SECURE_UNUSED_IFT_PASSWORD}"));
        assertEquals(Map.of("url", "jdbc:postgresql://dev.example.invalid/experiment",
                        "password", "${SECURE_DEV_EXPERIMENT_PASSWORD}"),
                CustomDatabaseConfigScope.selectedProperties(source, "dev", "experiment"));
    }

    @Test
    void iftDmNeverSilentlyUsesIft() {
        Properties source = properties(Map.of("db.ift.explab.url", "jdbc:postgresql://ift.example.invalid/test"));
        assertTrue(CustomDatabaseConfigScope.selectedProperties(source, "ift-dm", "explab").isEmpty());
        source.setProperty("db.ift-dm.explab.url", "jdbc:postgresql://ift-dm.example.invalid/test");
        assertEquals("jdbc:postgresql://ift-dm.example.invalid/test", CustomDatabaseConfigScope
                .selectedProperties(source, "ift-dm", "explab").getProperty("url"));
    }

    @Test
    void copiesNativeTimeoutAndPoolKeysWithoutIncludingUnrelatedProperties() {
        Properties source = properties(Map.of("db.dev.explab.timeout.in.seconds", "45",
                "db.dev.explab.connection.pool.size", "2", "db.dev.explab.unrelated", "not-a-client-key"));
        assertEquals(Map.of("timeout.in.seconds", "45", "connection.pool.size", "2"),
                CustomDatabaseConfigScope.selectedProperties(source, "DEV", "explab"));
    }

    private static Properties properties(Map<String,String> values) { Properties p = new Properties(); p.putAll(values); return p; }
}

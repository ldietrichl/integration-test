package config.services.core;

import java.util.Properties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import util.SplitterKafkaProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegressionProfileConfigurationTest {
    @ParameterizedTest
    @CsvSource({"dev,splitter_dev,dev-monitoring", "ift,splitter_ift,ift-monitoring", "eift-ds,splitter_ift,ift-monitoring"})
    void kafkaProfileAndTopicSelectTheSameEnvironment(String env, String expectedProfile, String expectedTopic) {
        Properties profiles = profiles();
        assertEquals(expectedProfile, RegressionProfileConfiguration.resolve(profiles, env, "splitter.config.kafka.env"));
        assertEquals(expectedTopic, RegressionProfileConfiguration.resolve(profiles, env, "splitter.config.kafka.monitoring.topic"));
    }

    @Test
    void unscopedDevValueDoesNotFillMissingIftProfile() {
        Properties profiles = profiles();
        profiles.remove("ift.splitter.config.kafka.env");
        profiles.setProperty("splitter.config.kafka.env", "splitter_dev");
        assertThrows(IllegalStateException.class,
                () -> RegressionProfileConfiguration.resolve(profiles, "ift", "splitter.config.kafka.env"));
    }

    @ParameterizedTest
    @CsvSource({"ift-dm", "lt", "local"})
    void otherEnvironmentsRequireTheirOwnKafkaProfile(String env) {
        assertThrows(IllegalStateException.class,
                () -> RegressionProfileConfiguration.resolve(profiles(), env, "splitter.config.kafka.env"));
    }

    @Test
    @ResourceLock("SYSTEM_PROPERTIES")
    void restoredKafkaHelpersIgnoreStaleJvmRouting() {
        String environment = TestEnvironment.current();
        Properties fileProfiles = TestConfigurationFiles.load("regression-profiles.properties");
        String[] routingKeys = {
                "splitter.config.kafka.env", "splitter.kap.kafka.env",
                "splitter.precalc.monitoring.kafka.env", "splitter.config.load.monitoring.kafka.env",
                "splitter.config.kafka.input.topic", "splitter.config.kafka.status.topic",
                "splitter.config.kafka.monitoring.topic", "splitter.kap.topic", "splitter.kap.monitoring.topic",
                "splitter.precalc.monitoring.topic", "splitter.config.load.monitoring.topic"
        };
        Map<String, String> previous = new HashMap<>();
        for (String key : routingKeys) previous.put(key, System.getProperty(key));
        previous.put("env", System.getProperty("env"));
        try {
            System.setProperty("env", "stale-invalid-environment");
            for (String key : routingKeys) {
                System.setProperty(key, "stale-other-environment");
                String expected = RegressionProfileConfiguration.resolve(fileProfiles, environment, key);
                assertEquals(expected, SplitterKafkaProperties.string(key, "unsafe-default"), key);
                if (key.endsWith(".env")) {
                    assertEquals(expected, SplitterKafkaProperties.kafkaEnv(key), key);
                }
            }
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) System.clearProperty(key);
                else System.setProperty(key, value);
            });
        }
    }

    private static Properties profiles() {
        Properties profiles = new Properties();
        profiles.setProperty("dev.splitter.config.kafka.env", "splitter_dev");
        profiles.setProperty("ift.splitter.config.kafka.env", "splitter_ift");
        profiles.setProperty("dev.splitter.config.kafka.monitoring.topic", "dev-monitoring");
        profiles.setProperty("ift.splitter.config.kafka.monitoring.topic", "ift-monitoring");
        return profiles;
    }
}

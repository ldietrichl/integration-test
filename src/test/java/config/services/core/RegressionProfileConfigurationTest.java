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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegressionProfileConfigurationTest {
    @ParameterizedTest
    @CsvSource({"ift-dm", "ift_dm", "eift-dm", "eift_dm"})
    void legacyDefaultHelperKeepsIftDmSeparate(String environment) {
        assertEquals("splitter_ift_dm", SplitterKafkaProperties.defaultKafkaEnv(environment));
    }

    @Test
    void legacyDefaultHelperRejectsMissingEnvironment() {
        assertThrows(IllegalArgumentException.class, () -> SplitterKafkaProperties.defaultKafkaEnv(null));
        assertThrows(IllegalArgumentException.class, () -> SplitterKafkaProperties.defaultKafkaEnv(""));
    }

    @Test
    void emptyIftDmTopicCannotUseIftTopicOrGlobalDefault() {
        Properties profiles = profiles();
        String key = "splitter.config.kafka.monitoring.topic";
        profiles.setProperty("ift-dm." + key, "");
        profiles.setProperty(key, "global-monitoring");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RegressionProfileConfiguration.resolve(profiles, "ift-dm", key));
        assertTrue(failure.getMessage().contains("ift-dm." + key));
    }

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
        Properties source = profiles();
        String key = "splitter.config.kafka.env";
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> RegressionProfileConfiguration.resolve(source, env, key));
        assertTrue(missing.getMessage().contains(env + "." + key));
        String ownProfile = "splitter_" + env.replace('-', '_');
        source.setProperty(env + "." + key, ownProfile);
        assertEquals(ownProfile, RegressionProfileConfiguration.resolve(source, env, key));
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
                String expected;
                try {
                    expected = RegressionProfileConfiguration.resolve(fileProfiles, environment, key);
                } catch (IllegalStateException missingProfile) {
                    // A stand used only by another suite need not configure splitter routing.
                    // Missing routing must reject stale JVM values and the caller's fallback.
                    IllegalStateException rejected = assertThrows(IllegalStateException.class,
                            () -> SplitterKafkaProperties.string(key, "unsafe-default"), key);
                    assertEquals(missingProfile.getMessage(), rejected.getMessage(), key);
                    if (key.endsWith(".env")) {
                        rejected = assertThrows(IllegalStateException.class,
                                () -> SplitterKafkaProperties.kafkaEnv(key), key);
                        assertEquals(missingProfile.getMessage(), rejected.getMessage(), key);
                    }
                    continue;
                }
                assertEquals(expected, SplitterKafkaProperties.string(key, "unsafe-default"), key);
                if (key.endsWith(".env")) assertEquals(expected, SplitterKafkaProperties.kafkaEnv(key), key);
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

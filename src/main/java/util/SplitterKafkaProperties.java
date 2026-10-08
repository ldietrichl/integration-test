package util;

import java.time.Duration;

import config.services.core.RegressionProfileConfiguration;
import config.services.core.TestEnvironment;

public final class SplitterKafkaProperties {

    private SplitterKafkaProperties() {
    }

    public static String kafkaEnv(String propertyName) {
        return RegressionProfileConfiguration.required(propertyName);
    }

    public static String string(String propertyName, String defaultValue) {
        if (RegressionProfileConfiguration.isRoutingKey(propertyName)) {
            return RegressionProfileConfiguration.required(propertyName);
        }
        String configured = usable(System.getProperty(propertyName));
        return configured == null ? defaultValue : configured;
    }

    public static boolean bool(String propertyName, boolean defaultValue) {
        String configured = usable(System.getProperty(propertyName));
        return configured == null ? defaultValue : Boolean.parseBoolean(configured);
    }

    public static Duration durationSeconds(String propertyName, Duration defaultValue) {
        String configured = usable(System.getProperty(propertyName));
        if (configured == null) {
            return defaultValue;
        }
        return Duration.ofSeconds(Long.parseLong(configured));
    }

    public static String defaultKafkaEnv(String testEnv) {
        return "splitter_" + TestEnvironment.normalize(testEnv).replace('-', '_');
    }

    private static String usable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

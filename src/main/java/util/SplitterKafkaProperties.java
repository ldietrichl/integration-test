package util;

import java.time.Duration;
import java.util.Locale;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;

public final class SplitterKafkaProperties {

    private SplitterKafkaProperties() {
    }

    public static String kafkaEnv(String propertyName) {
        return string(propertyName, defaultKafkaEnv(TEST_CONFIG.env()));
    }

    public static String string(String propertyName, String defaultValue) {
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
        String normalized = testEnv == null
                ? ""
                : testEnv.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        if ("dev".equals(normalized)) {
            return "splitter_dev";
        }
        if ("ift".equals(normalized)
                || "ift_ds".equals(normalized)
                || "ift_dm".equals(normalized)
                || "eift".equals(normalized)
                || "eift_ds".equals(normalized)
                || "eift_dm".equals(normalized)) {
            return "splitter_ift";
        }
        return usable(testEnv) == null ? "splitter_dev" : testEnv.trim();
    }

    private static String usable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

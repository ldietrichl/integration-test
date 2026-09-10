package config.services.core;

import java.util.Properties;
import java.util.Set;

/** Environment-scoped Kafka routing shared by direct IDEA tests and Gradle regressions. */
public final class RegressionProfileConfiguration {
    private static final Set<String> ROUTING_KEYS = Set.of(
            "splitter.config.kafka.env", "splitter.kap.kafka.env",
            "splitter.precalc.monitoring.kafka.env", "splitter.config.load.monitoring.kafka.env",
            "splitter.config.kafka.input.topic", "splitter.config.kafka.status.topic",
            "splitter.config.kafka.monitoring.topic", "splitter.kap.topic", "splitter.kap.monitoring.topic",
            "splitter.precalc.monitoring.topic", "splitter.config.load.monitoring.topic");
    private static final Properties PROFILES = TestConfigurationFiles.load("regression-profiles.properties");

    private RegressionProfileConfiguration() { }

    public static boolean isRoutingKey(String key) { return ROUTING_KEYS.contains(key); }

    public static String required(String key) {
        return resolve(PROFILES, TestEnvironment.current(), key);
    }

    static String resolve(Properties profiles, String environment, String key) {
        if (!isRoutingKey(key)) throw new IllegalArgumentException("Unknown regression routing key: " + key);
        String scoped = TestEnvironment.normalize(environment) + "." + key;
        String value = profiles.getProperty(scoped);
        if (value == null || value.isBlank() || value.trim().startsWith("<")) {
            throw new IllegalStateException("Configure " + scoped + " in src/test/resources/regression-profiles.properties");
        }
        return value.trim();
    }
}

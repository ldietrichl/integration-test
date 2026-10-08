package config.services.core;

import io.perfeccionista.framework.Environment;
import java.util.Properties;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.kafka.DefaultKafkaServiceConfiguration;
import ru.sber.qa.services.kafka.KafkaClientConfiguration;

/** Native Platform V AT files and clients; resolve secrets only for the requested named profile. */
public class ProjectKafkaConfiguration extends DefaultKafkaServiceConfiguration {
    @Override public KafkaClientConfiguration getKafkaConsumerClientConfiguration(Environment environment, String name) {
        return configuration(environment, "kafka-consumers.properties", "kafka_consumer.", name, name);
    }

    @Override public KafkaClientConfiguration getKafkaProducerClientConfiguration(Environment environment, String name) {
        return configuration(environment, "kafka-producers.properties", "kafka_producer.", name, name);
    }

    protected final KafkaClientConfiguration configuration(Environment environment, String file, String prefix,
            String profile, String clientName) {
        Properties raw = selectedProperties(readProperties(file), prefix, profile);
        // Existing splitter captures create a fresh observer group for a single test run.
        // Connection/security properties remain owned by the native property files.
        if (prefix.equals("kafka_consumer.")) {
            String group = System.getProperty(prefix + profile + ".group.id");
            if (group != null && !group.isBlank()) raw.setProperty("group.id", group);
        }
        Properties resolved = environment.getService(ConfigurationService.class).getProperties(() -> raw);
        if (resolved.getProperty("bootstrap.servers", "").isBlank())
            throw new IllegalStateException("Missing bootstrap.servers for Kafka profile " + profile + " in " + file);
        var config = new KafkaClientConfiguration(clientName);
        resolved.stringPropertyNames().forEach(key -> config.setProperty(key, resolved.getProperty(key)));
        return config;
    }

    protected Properties readProperties(String file) { return TestConfigurationFiles.load(file); }

    static Properties selectedProperties(Properties source, String prefix, String profile) {
        if (profile == null || !profile.matches("\\w+"))
            throw new IllegalArgumentException("Kafka profile must use letters, digits or underscores");
        String selectedPrefix = prefix + profile + ".";
        if (source.stringPropertyNames().stream().noneMatch(key -> key.startsWith(selectedPrefix)))
            throw new IllegalStateException("Kafka profile is not configured: " + profile);
        Properties selected = new Properties();
        for (String name : new String[]{"all", profile}) {
            String propertyPrefix = prefix + name + ".";
            source.stringPropertyNames().stream().filter(key -> key.startsWith(propertyPrefix)).forEach(key ->
                    selected.setProperty(key.substring(propertyPrefix.length()), source.getProperty(key)));
        }
        return selected;
    }
}

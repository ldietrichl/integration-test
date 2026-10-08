package config.services.core;

import io.perfeccionista.framework.Environment;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sber.qa.services.configuration.ConfigurationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectKafkaConfigurationTest {
    @Test
    void mergesCommonSettingsAndOnlyTheExplicitSelectedProfile() {
        Properties source = properties(Map.of(
                "kafka_consumer.all.enable.auto.commit", "false",
                "kafka_consumer.all.group.id", "common-group",
                "kafka_consumer.dev.bootstrap.servers", "dev.example.invalid:9092",
                "kafka_consumer.dev.group.id", "dev-group",
                "kafka_consumer.ift.bootstrap.servers", "ift.example.invalid:9092",
                "kafka_consumer.ift.ssl.key.password", "${SECURE_UNUSED_IFT_PASSWORD}"));
        Properties selected = ProjectKafkaConfiguration.selectedProperties(source, "kafka_consumer.", "dev");
        assertEquals(Map.of("enable.auto.commit", "false", "group.id", "dev-group",
                "bootstrap.servers", "dev.example.invalid:9092"), selected);
    }

    @Test
    void missingNamedProfileNeverFallsBackToTheCommonBroker() {
        Properties source = properties(Map.of("kafka_consumer.all.bootstrap.servers", "common.example.invalid:9092"));
        assertThrows(IllegalStateException.class,
                () -> ProjectKafkaConfiguration.selectedProperties(source, "kafka_consumer.", "ift"));
    }

    @Test
    void iftDmRequiresItsOwnUnderscoreProfile() {
        Properties source = properties(Map.of("kafka_consumer.ift.bootstrap.servers", "ift.example.invalid:9092",
                "kafka_consumer.ift_dm.bootstrap.servers", "ift-dm.example.invalid:9092"));
        assertEquals("ift-dm.example.invalid:9092", ProjectKafkaConfiguration
                .selectedProperties(source, "kafka_consumer.", "ift_dm").getProperty("bootstrap.servers"));
        assertThrows(IllegalArgumentException.class,
                () -> ProjectKafkaConfiguration.selectedProperties(source, "kafka_consumer.", "ift-dm"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ift_dm", "splitter_ift_dm"})
    void unconfiguredIftDmBrokerCannotInheritCommonOrIftBroker(String profile) {
        var configuration = configuration(properties(Map.of(
                "kafka_consumer.all.bootstrap.servers", "common.example.invalid:9092",
                "kafka_consumer.ift.bootstrap.servers", "ift.example.invalid:9092",
                "kafka_consumer.splitter_ift.bootstrap.servers", "splitter-ift.example.invalid:9092",
                "kafka_consumer." + profile + ".bootstrap.servers", "")));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> configuration.getKafkaConsumerClientConfiguration(environment(), profile));
        assertTrue(failure.getMessage().contains(profile));
    }

    @Test
    void producerAndConsumerNamespacesRemainSeparate() {
        Properties source = properties(Map.of("kafka_producer.all.acks", "all",
                "kafka_producer.dev.bootstrap.servers", "producer.example.invalid:9092",
                "kafka_consumer.dev.bootstrap.servers", "consumer.example.invalid:9092"));
        assertEquals(Map.of("acks", "all", "bootstrap.servers", "producer.example.invalid:9092"),
                ProjectKafkaConfiguration.selectedProperties(source, "kafka_producer.", "dev"));
    }

    @Test
    void frameworkConfigurationServiceResolvesOnlyTheSelectedProfile() {
        var configuration = configuration(properties(Map.of(
                "kafka_consumer.dev.bootstrap.servers", "dev.example.invalid:9092",
                "kafka_consumer.ift.ssl.key.password", "${SECURE_UNUSED_IFT_PASSWORD}")));
        var client = configuration.getKafkaConsumerClientConfiguration(environment(), "dev");
        assertEquals("dev", client.getName());
        assertEquals("dev.example.invalid:9092", client.getProperties().getProperty("bootstrap.servers"));
        assertFalse(client.getProperties().containsKey("ssl.key.password"));
    }

    @Test
    @ResourceLock("SYSTEM_PROPERTIES")
    void runtimeGroupIdIsPreservedButConnectionOverridesAreIgnored() {
        String groupKey = "kafka_consumer.dev.group.id", brokerKey = "kafka_consumer.dev.bootstrap.servers";
        String previousGroup = System.getProperty(groupKey), previousBroker = System.getProperty(brokerKey);
        try {
            System.setProperty(groupKey, "observer-unique-synthetic");
            System.setProperty(brokerKey, "stale.example.invalid:9092");
            var configuration = configuration(properties(Map.of(
                    brokerKey, "file.example.invalid:9092", groupKey, "configured-group")));
            var values = configuration.getKafkaConsumerClientConfiguration(environment(), "dev").getProperties();
            assertEquals("file.example.invalid:9092", values.getProperty("bootstrap.servers"));
            assertEquals("observer-unique-synthetic", values.getProperty("group.id"));
        } finally {
            restore(groupKey, previousGroup); restore(brokerKey, previousBroker);
        }
    }

    private static ProjectKafkaConfiguration configuration(Properties source) {
        return new ProjectKafkaConfiguration() {
            @Override protected Properties readProperties(String file) { return source; }
        };
    }
    private static Environment environment() {
        Environment environment = mock(Environment.class);
        when(environment.getService(ConfigurationService.class)).thenReturn(new SecureAwareConfigurationService());
        return environment;
    }
    private static Properties properties(Map<String,String> values) { Properties p = new Properties(); p.putAll(values); return p; }
    private static void restore(String key, String previous) {
        if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
    }
}

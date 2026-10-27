package config.services.core;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.kafka.KafkaClientConfiguration;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;
import java.util.UUID;

/** Independent observers using the existing native Kafka profile loader. */
public final class ExperimentLinksKafkaConfiguration extends ProjectKafkaConfiguration {
    private static final String IFT_SECURITY_PROTOCOL = "PLAINTEXT";

    private static Properties configuredConsumer(String profile) {
        Properties source = TestConfigurationFiles.load("kafka-consumers.properties");
        Properties selected = selectedProperties(source, "kafka_consumer.", profile);
        // The observer listener is explicitly selected; do not infer transport from its port or a common default.
        requireTransport(source.getProperty("kafka_consumer." + profile + ".security.protocol"));
        return selected;
    }

    private static void requireTransport(String protocol) {
        if (protocol == null || !IFT_SECURITY_PROTOCOL.equals(protocol.trim()))
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: Kafka ExpLab ИФТ требует явно заданный "
                    + "security.protocol=PLAINTEXT; SSL/SASL и неявный общий default не применяются");
    }

    private static void requireResolvedConsumer(Properties selected, Properties resolved) {
        requireTransport(resolved.getProperty("security.protocol"));
        if (!endpoints(selected.getProperty("bootstrap.servers")).equals(endpoints(resolved.getProperty("bootstrap.servers"))))
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: разрешённые настройки Kafka изменили bootstrap выбранного профиля ExpLab");
    }

    /** Compare the selected observer listener with the configured producer listener on the same broker hosts. */
    public static Map<String, String> requireProducerBootstrap(String producerBootstrap) {
        String profile = new ExperimentLinksSettings().kafkaProfile();
        Properties selected = configuredConsumer(profile);
        String bootstrap = selected.getProperty("bootstrap.servers", "").trim();
        if (!producerEndpointsForObserver(bootstrap).equals(endpoints(producerBootstrap)))
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: Kafka-профиль " + profile
                    + " не совпадает с configured producer-explab bootstrap текущего workload; consumer="
                    + bootstrap + "; configured producer=" + producerBootstrap);
        return Map.of("profile", profile, "consumerBootstrap", bootstrap,
                "consumerSecurityProtocol", selected.getProperty("security.protocol"),
                "configuredProducerBootstrap", producerBootstrap,
                "listenerMapping", "Explicit observer :9092 -> configured producer :9093 on the same broker hosts; cluster identity is not proven",
                "source", "Ready workload configured environment binding; external YAML/JVM overrides are not inspected");
    }

    private static Set<String> producerEndpointsForObserver(String bootstrap) {
        Set<String> result = new TreeSet<>();
        String observerSuffix = ":9092";
        for (String endpoint : endpoints(bootstrap)) {
            if (!endpoint.endsWith(observerSuffix))
                throw new IllegalStateException("BLOCKED_ENVIRONMENT: выбранный listener Kafka observer должен использовать порт 9092");
            result.add(endpoint.substring(0, endpoint.length() - observerSuffix.length()) + ":9093");
        }
        return Set.copyOf(result);
    }

    private static Set<String> endpoints(String bootstrap) {
        if (bootstrap == null || bootstrap.isBlank())
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: отсутствует Kafka bootstrap");
        Set<String> result = new TreeSet<>();
        for (String address : bootstrap.split(",", -1)) {
            try {
                URI uri = URI.create("kafka://" + address.trim());
                if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                        || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || (uri.getPath() != null && !uri.getPath().isEmpty()))
                    throw new IllegalArgumentException();
                result.add(uri.getHost().toLowerCase(Locale.ROOT) + ":" + uri.getPort());
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("BLOCKED_ENVIRONMENT: некорректный Kafka bootstrap endpoint");
            }
        }
        return Set.copyOf(result);
    }
    @Override public KafkaClientConfiguration getKafkaConsumerClientConfiguration(Environment environment, String name) {
        String profile = new ExperimentLinksSettings().kafkaProfile();
        Properties selected = configuredConsumer(profile);
        KafkaClientConfiguration client = configuration(environment, "kafka-consumers.properties", "kafka_consumer.", profile, name);
        requireResolvedConsumer(selected, client.getProperties());
        return client
                .setProperty("group.id", "experiment-links-" + UUID.randomUUID())
                .setProperty("enable.auto.commit", "false")
                .setProperty("auto.offset.reset", "latest")
                .setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
                .setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    }
}

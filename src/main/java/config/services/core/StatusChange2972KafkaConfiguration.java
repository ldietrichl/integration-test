package config.services.core;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.kafka.KafkaClientConfiguration;
import java.util.UUID;

/** The selected test environment identifies a native Kafka consumer profile; captures own independent clients. */
public final class StatusChange2972KafkaConfiguration extends ProjectKafkaConfiguration {
    @Override public KafkaClientConfiguration getKafkaConsumerClientConfiguration(Environment environment, String name) {
        String profile = TestEnvironment.current().replace('-', '_');
        return configuration(environment, "kafka-consumers.properties", "kafka_consumer.", profile, name)
                .setProperty("group.id", "explab2972-" + UUID.randomUUID())
                .setProperty("enable.auto.commit", "false")
                .setProperty("auto.offset.reset", "latest")
                .setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
                .setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    }
}

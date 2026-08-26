package util;

import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.matchers.core.ValidatableResponseWrapperMatcher;

import java.time.Duration;

/**
 * Набор обёрток, чтобы Kafka-проверки выглядели как matchers в .should(...)
 */
public final class KafkaRestMatchers {

    private KafkaRestMatchers() {
    }

    /**
     * Матчер, который вызывает KafkaQueueAsserts внутри .should(...)
     */
    public static ValidatableResponseWrapperMatcher hasKafkaFirstPayloadByHostnameSince(
            String envName,
            String topic,
            String expectedSubstring,
            String hostnameSubstring,
            long sinceEpochMillis,
            KafkaService kafkaService,
            Duration timeout
    ) {
        return r -> KafkaQueueAsserts.assertFirstPayloadContainsByHostnameSince(
                envName,
                topic,
                expectedSubstring,
                hostnameSubstring,
                sinceEpochMillis,
                kafkaService,
                timeout
        );
    }


    public static ValidatableResponseWrapperMatcher hasKafkaFirstPayloadPayload(
            String envName,
            String topic,
            String expectedSubstring,
            long sinceEpochMillis,
            KafkaService kafkaService,
            Duration timeout
    ) {
        return r -> KafkaQueueAsserts.assertFirstPayload(
                envName,
                topic,
                expectedSubstring,
                sinceEpochMillis,
                kafkaService,
                timeout
        );
    }
}

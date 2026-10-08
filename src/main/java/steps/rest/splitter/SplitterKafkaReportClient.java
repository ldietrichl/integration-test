package steps.rest.splitter;

import com.fasterxml.jackson.databind.JsonNode;
import config.services.core.RegressionProfileConfiguration;
import io.qameta.allure.Allure;
import ru.sber.qa.services.kafka.KafkaConsumerClient;
import ru.sber.qa.services.kafka.KafkaService;
import util.KafkaAllureLog;
import java.time.Duration;

/** A bounded report observation positioned before split; KafkaService owns the consumer. */
public final class SplitterKafkaReportClient implements AutoCloseable {
    private final KafkaConsumerClient<?, ?> consumer;
    private final Duration timeout;
    private final String env, topic;
    private final BoundedKafkaObservation observation = new BoundedKafkaObservation();

    public SplitterKafkaReportClient(KafkaService kafka) {
        this(kafka.consumerClient(RegressionProfileConfiguration.required("splitter.kap.kafka.env"), configuredTimeout()),
                RegressionProfileConfiguration.required("splitter.kap.kafka.env"),
                RegressionProfileConfiguration.required("splitter.kap.topic"), configuredTimeout());
    }
    private static Duration configuredTimeout() {
        int seconds = Integer.parseInt(System.getProperty("splitter.kap.timeout.seconds", "30"));
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("Report timeout must be 1..300 seconds");
        return Duration.ofSeconds(seconds);
    }
    SplitterKafkaReportClient(KafkaConsumerClient<?, ?> consumer, String env, String topic, Duration timeout) {
        this.consumer = consumer; this.env = env; this.topic = topic; this.timeout = timeout;
        try {
            consumer.assign(topic);
            var client = consumer.getConsumerClient();
            var partitions = client.assignment();
            if (partitions.isEmpty()) throw new IllegalStateException("No report partitions assigned: " + topic);
            client.seekToEnd(partitions);
            // seekToEnd is lazy: resolve every position before the caller sends split.
            for (var partition : partitions) client.position(partition, timeout);
            consumer.clear();
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public JsonNode await(String requestId, String point, long version) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (KafkaAllureLog.Scope ignored = KafkaAllureLog.waitingForTopic(env, topic, timeout,
                "split report " + requestId + " / " + point + " / " + version)) {
            while (System.nanoTime() < deadline) {
                JsonNode found = BoundedKafkaObservation.batch(consumer, Duration.ofMillis(300), () -> {
                    var records = consumer.records().iterator();
                    while (records.hasNext()) {
                        var wrapper = records.next();
                        Object value = wrapper.toConsumerRecord().value();
                        if (value == null) continue;
                        String payload = value.toString();
                        if (!observation.record("report", payload)) continue;
                        var parsed = BoundedKafkaObservation.parse(payload);
                        if (parsed.isPresent() && matches(parsed.get(), requestId, point, version)) return parsed.get();
                    }
                    return null;
                });
                if (found != null) {
                    Allure.addAttachment("КАП / " + point + " / " + requestId, "application/json", found.toString(), ".json");
                    return found;
                }
            }
        }
        throw new AssertionError("Missing correlated report: requestId=" + requestId + ", point=" + point
                + ", version=" + version + ", topic=" + topic + "\n" + observation.summary());
    }

    static boolean matches(JsonNode envelope, String requestId, String point, long version) {
        JsonNode message = envelope.path("message");
        return requestId.equals(envelope.path("requestId").asText(null))
                && requestId.equals(message.path("requestId").asText(null))
                && point.equals(envelope.path("messageInfo").path("splittingPoint").asText(null))
                && message.path("splittingConfigVersion").isIntegralNumber()
                && message.path("splittingConfigVersion").longValue() == version;
    }

    @Override public void close() {
        try { consumer.clear(); } finally { consumer.unsubscribe(); }
    }
}

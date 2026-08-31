package ru.sber.qa.splitter.EXPLAB_2399;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.splitter.config.LoadConfigRequestDto;
import io.qameta.allure.Allure;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import ru.sber.qa.services.kafka.KafkaService;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static util.TestAssertions.fail;

final class SplitterConfigKafkaLoad2399Flow {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String DEFAULT_INPUT_TOPIC = "splitting-config-created";
    private static final String DEFAULT_STATUS_TOPIC = "splitting-config-requested-and-received";
    private static final String DEFAULT_MONITORING_TOPIC = "omon_explab_splitter_log";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45);

    String kafkaEnv() {
        return System.getProperty("splitter.config.kafka.env", TEST_CONFIG.env());
    }

    String inputTopic() {
        return System.getProperty("splitter.config.kafka.input.topic", DEFAULT_INPUT_TOPIC);
    }

    String statusTopic() {
        return System.getProperty("splitter.config.kafka.status.topic", DEFAULT_STATUS_TOPIC);
    }

    String monitoringTopic() {
        return System.getProperty("splitter.config.kafka.monitoring.topic", DEFAULT_MONITORING_TOPIC);
    }

    boolean isStatusRequired() {
        return Boolean.parseBoolean(System.getProperty("splitter.config.kafka.status.required", "true"));
    }

    Duration timeout() {
        return Duration.ofSeconds(Long.parseLong(System.getProperty(
                "splitter.config.kafka.timeout.seconds",
                String.valueOf(DEFAULT_TIMEOUT.toSeconds()))));
    }

    void sendConfig(LoadConfigRequestDto request) {
        sendJson(request.getMessageId(), toJson(request));
    }

    void sendRaw(String messageKey, String payload) {
        sendJson(messageKey, payload);
    }

    JsonNode findStatusByConfigMessageId(KafkaService kafkaService,
                                         String configMessageId,
                                         long sinceEpochMillis) {
        return findJsonNode(kafkaService,
                statusTopic(),
                sinceEpochMillis,
                node -> configMessageId.equals(text(node, "configMessageId"))
                        && "STATUS".equals(normalizedText(node, "messageType")),
                "status by configMessageId=" + configMessageId);
    }

    JsonNode findMonitoringByMessageIdAndResult(KafkaService kafkaService,
                                                String messageId,
                                                String result,
                                                long sinceEpochMillis) {
        return findJsonNode(kafkaService,
                monitoringTopic(),
                sinceEpochMillis,
                node -> "SPLITTING_CONFIG_LOAD".equals(normalizedText(node, "function"))
                        && result.equals(normalizedText(node, "result"))
                        && messageIdMatches(node, messageId),
                "monitoring result=" + result + ", messageId/requestIdIn=" + messageId);
    }

    JsonNode findMonitoringByResult(KafkaService kafkaService,
                                    String result,
                                    long sinceEpochMillis) {
        return findJsonNode(kafkaService,
                monitoringTopic(),
                sinceEpochMillis,
                node -> "SPLITTING_CONFIG_LOAD".equals(normalizedText(node, "function"))
                        && result.equals(normalizedText(node, "result")),
                "monitoring result=" + result);
    }

    private void sendJson(String key, String payload) {
        String env = kafkaEnv();
        String topic = inputTopic();
        Duration timeout = timeout();

        Allure.parameter("splitter.config.kafka.env", env);
        Allure.parameter("splitter.config.kafka.inputTopic", topic);
        Allure.addAttachment("Kafka input payload / " + topic, "application/json", payload, ".json");

        Properties properties = producerProperties(env);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, key, payload))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            producer.flush();
        } catch (Exception exception) {
            throw new AssertionError("Не удалось отправить сообщение в Kafka topic=" + topic
                    + ", env=" + env
                    + ". Проверь kafka_producer." + env + ".bootstrap.servers или переопредели через -Dkafka_producer."
                    + env + ".bootstrap.servers", exception);
        }
    }

    private JsonNode findJsonNode(KafkaService kafkaService,
                                  String topic,
                                  long sinceEpochMillis,
                                  Predicate<JsonNode> predicate,
                                  String assertionContext) {
        String env = kafkaEnv();
        Duration timeout = timeout();
        Allure.parameter("splitter.config.kafka.consumerEnv", env);
        Allure.parameter("splitter.config.kafka.topic", topic);
        Allure.parameter("splitter.config.kafka.since", String.valueOf(sinceEpochMillis));
        Allure.parameter("splitter.config.kafka.timeout", timeout.toString());

        var consumer = kafkaService.consumerClient(env, timeout);
        List<String> observedPayloads = new ArrayList<>();
        try {
            consumer.subscribe(topic);
            consumer.poll(Duration.ofMillis(300));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(300));
                List<JsonNode> matched = new ArrayList<>();
                consumer.records().forEach(recordWrapper -> {
                    var record = recordWrapper.toConsumerRecord();
                    Object raw = record.value();
                    if (raw == null) {
                        return;
                    }
                    long recordTimestamp = record.timestamp();
                    if (recordTimestamp > 0 && recordTimestamp < sinceEpochMillis) {
                        return;
                    }

                    String payload = unescapeUnicode(String.valueOf(raw));
                    observedPayloads.add(payload);
                    extractObjectNodes(payload).stream()
                            .filter(predicate)
                            .forEach(matched::add);
                });

                if (!matched.isEmpty()) {
                    JsonNode node = matched.get(0);
                    Allure.addAttachment("Kafka matched payload / " + assertionContext,
                            "application/json",
                            pretty(node),
                            ".json");
                    return node;
                }
            }
        } finally {
            try {
                consumer.unsubscribe();
            } catch (Throwable ignored) {
            }
        }

        String sample = observedPayloads.stream()
                .limit(10)
                .collect(Collectors.joining("\n---\n"));
        Allure.addAttachment("Kafka observed sample / " + assertionContext,
                "text/plain",
                sample.isBlank() ? "No messages after since=" + sinceEpochMillis : sample,
                ".txt");
        fail("Не найдено Kafka-сообщение: " + assertionContext
                + "\nTopic=" + topic
                + "\nEnv=" + env
                + "\nSince=" + sinceEpochMillis
                + "\nObserved sample:\n" + sample);
        return null;
    }

    private static Properties producerProperties(String env) {
        Properties source = new Properties();
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("kafka-producers.properties")) {
            if (inputStream != null) {
                source.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать kafka-producers.properties", exception);
        }

        Properties target = new Properties();
        applyPrefix(source, target, "kafka_producer.all.");
        applyPrefix(source, target, "kafka_producer." + env + ".");
        applySystemPrefix(target, "kafka_producer.all.");
        applySystemPrefix(target, "kafka_producer." + env + ".");

        target.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        target.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        target.putIfAbsent(ProducerConfig.ACKS_CONFIG, "1");

        Object bootstrapServers = target.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
        if (bootstrapServers == null || String.valueOf(bootstrapServers).isBlank()) {
            throw new AssertionError("Не заданы bootstrap.servers для Kafka producer env=" + env
                    + ". Добавь kafka_producer." + env + ".bootstrap.servers в kafka-producers.properties"
                    + " или передай -Dkafka_producer." + env + ".bootstrap.servers=<hosts>");
        }
        return target;
    }

    private static void applyPrefix(Properties source, Properties target, String prefix) {
        source.stringPropertyNames().stream()
                .filter(name -> name.startsWith(prefix))
                .forEach(name -> target.put(name.substring(prefix.length()), source.getProperty(name)));
    }

    private static void applySystemPrefix(Properties target, String prefix) {
        System.getProperties().stringPropertyNames().stream()
                .filter(name -> name.startsWith(prefix))
                .forEach(name -> target.put(name.substring(prefix.length()), System.getProperty(name)));
    }

    private static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать Kafka DTO в JSON", exception);
        }
    }

    private static List<JsonNode> extractObjectNodes(String payload) {
        List<JsonNode> result = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            collectCandidateJsonRoots(root).forEach(candidate -> collectObjectNodes(candidate, result));
        } catch (JsonProcessingException ignored) {
            // Не JSON или битый JSON в топике не должен ронять поиск по остальным сообщениям.
        }
        return result;
    }

    private static List<JsonNode> collectCandidateJsonRoots(JsonNode root) {
        List<JsonNode> roots = new ArrayList<>();
        roots.add(root);
        if (root != null && root.isTextual()) {
            parseJson(root.asText()).ifPresent(roots::add);
        }
        if (root != null && root.isObject()) {
            JsonNode message = root.path("message");
            if (message.isTextual()) {
                parseJson(message.asText()).ifPresent(roots::add);
            } else if (message.isObject()) {
                roots.add(message);
            }
        }
        return roots;
    }

    private static java.util.Optional<JsonNode> parseJson(String raw) {
        try {
            return java.util.Optional.of(OBJECT_MAPPER.readTree(raw));
        } catch (JsonProcessingException exception) {
            return java.util.Optional.empty();
        }
    }

    private static void collectObjectNodes(JsonNode node, List<JsonNode> result) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            result.add(node);
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                collectObjectNodes(fields.next().getValue(), result);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectObjectNodes(child, result));
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    static String normalizedText(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null) {
            return null;
        }
        return raw.trim().replaceFirst("^'+", "");
    }

    static String textAny(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean messageIdMatches(JsonNode node, String messageId) {
        return Objects.equals(messageId, text(node, "messageId"))
                || Objects.equals(messageId, text(node, "requestIdIn"))
                || Objects.equals(messageId, text(node, "configMessageId"));
    }

    private static String pretty(JsonNode node) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return String.valueOf(node);
        }
    }

    private static String unescapeUnicode(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (c == '\\' && i + 5 < s.length() && s.charAt(i + 1) == 'u') {
                String hex = s.substring(i + 2, i + 6);
                try {
                    int code = Integer.parseInt(hex, 16);
                    out.append((char) code);
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {
                    // fallback ниже
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}



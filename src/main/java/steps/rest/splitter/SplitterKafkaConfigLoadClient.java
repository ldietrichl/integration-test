package steps.rest.splitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.splitter.config.LoadConfigRequestDto;
import io.qameta.allure.Allure;
import io.restassured.builder.ResponseBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import ru.sber.qa.services.rest.validation.DefaultValidatableResponseWrapper;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;

final class SplitterKafkaConfigLoadClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile("\"messageId\"\\s*:\\s*\"([^\"]+)\"");

    private static final String DEFAULT_INPUT_TOPIC = "splitting-config-created";
    private static final String DEFAULT_STATUS_TOPIC = "splitting-config-requested-and-received";
    private static final String DEFAULT_MONITORING_TOPIC = "omon_explab_splitter_log";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45);

    private SplitterKafkaConfigLoadClient() {
    }

    static ValidatableResponseWrapper load(Object body, String endpointSplittingPointCode) {
        return DefaultValidatableResponseWrapper.defaultValidatableRestResponse(
                loadResponse(body, endpointSplittingPointCode));
    }

    static Response loadResponse(Object body, String endpointSplittingPointCode) {
        Payload payload = payload(body);
        String messageKey = messageKey(payload);
        boolean endpointMismatch = isEndpointMismatch(payload, endpointSplittingPointCode);
        boolean badRequestByRequestContract = isBadRequestByRequestContract(payload);

        Allure.parameter("splitter.config.load.mode", "kafka");
        Allure.parameter("splitter.config.kafka.endpointSplittingPointCode", endpointSplittingPointCode);
        Allure.parameter("splitter.config.kafka.messageKey", messageKey);
        Allure.addAttachment("Kafka input payload / " + inputTopic(), "application/json", payload.raw(), ".json");

        if (payload.malformedJson()) {
            sendJson(messageKey, payload.raw());
            return response(KafkaLoadOutcome.badRequest(payload, messageKey, "Malformed JSON"));
        }

        if (badRequestByRequestContract) {
            sendJson(messageKey, payload.raw());
            return response(KafkaLoadOutcome.badRequest(payload, messageKey, "Request violates splitter config load contract"));
        }

        if (endpointMismatch) {
            sendJson(messageKey, payload.raw());
            return response(KafkaLoadOutcome.endpointMismatch(payload, messageKey, endpointSplittingPointCode));
        }

        KafkaLoadOutcome outcome = sendAndWait(messageKey, payload, badRequestByRequestContract);
        return response(outcome);
    }

    private static Payload payload(Object body) {
        String raw = toJson(body);
        JsonNode node = parseJson(raw).orElse(null);
        if (body instanceof String) {
            node = parseJson((String) body).orElse(null);
            return new Payload((String) body, node, node == null);
        }
        return new Payload(raw, node, false);
    }

    private static String messageKey(Payload payload) {
        String id = firstNonBlank(
                text(payload.root(), "messageId"),
                text(payload.root(), "requestId"),
                messageIdFromRaw(payload.raw())
        );
        return id == null ? UUID.randomUUID().toString() : id;
    }

    private static boolean isEndpointMismatch(Payload payload, String endpointSplittingPointCode) {
        String requestSplittingPointCode = normalizedText(payload.root(), "splittingPointCode");
        return requestSplittingPointCode != null
                && endpointSplittingPointCode != null
                && !requestSplittingPointCode.equalsIgnoreCase(endpointSplittingPointCode);
    }

    private static boolean isBadRequestByRequestContract(Payload payload) {
        if (payload.malformedJson()) {
            return true;
        }
        JsonNode root = payload.root();
        if (root == null || root.isNull()) {
            return true;
        }
        if (!root.hasNonNull("splittingPointCode")) {
            return true;
        }
        JsonNode config = root.path("splittingConfig");
        if (!config.isObject()) {
            return true;
        }
        JsonNode experiments = config.path("experiments");
        if (!experiments.isArray()) {
            return true;
        }
        for (JsonNode experiment : experiments) {
            if (!experiment.hasNonNull("salt") && !experiment.hasNonNull("layerId")) {
                return true;
            }
            JsonNode conditions = experiment.path("objectSelectConditions");
            if (!conditions.isArray() || conditions.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static KafkaLoadOutcome sendAndWait(String messageKey, Payload payload, boolean badRequestByRequestContract) {
        String observeTopic = statusRequired() ? statusTopic() : monitoringTopic();
        Properties consumerProperties = consumerProperties(kafkaEnv());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(Collections.singletonList(observeTopic));
            assignAtEnd(consumer, observeTopic);

            long since = System.currentTimeMillis();
            sendJson(messageKey, payload.raw());
            return findOutcome(consumer, observeTopic, payload, messageKey, since, badRequestByRequestContract);
        }
    }

    private static KafkaLoadOutcome findOutcome(KafkaConsumer<String, String> consumer,
                                                String topic,
                                                Payload payload,
                                                String messageKey,
                                                long sinceEpochMillis,
                                                boolean badRequestByRequestContract) {
        Duration timeout = timeout();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        List<String> observedPayloads = new ArrayList<>();

        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(300))) {
                if (record.timestamp() > 0 && record.timestamp() < sinceEpochMillis) {
                    continue;
                }
                if (record.value() == null) {
                    continue;
                }

                String raw = unescapeUnicode(record.value());
                observedPayloads.add(raw);
                Optional<KafkaLoadOutcome> outcome = extractObjectNodes(raw).stream()
                        .map(node -> outcomeFromNode(node, payload, messageKey, badRequestByRequestContract))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst();
                if (outcome.isPresent()) {
                    Allure.addAttachment("Kafka matched payload / " + topic,
                            "application/json",
                            pretty(outcome.get().sourceNode()),
                            ".json");
                    return outcome.get();
                }
            }
        }

        String sample = observedPayloads.stream()
                .limit(10)
                .collect(Collectors.joining("\n---\n"));
        Allure.addAttachment("Kafka observed sample / " + topic,
                "text/plain",
                sample.isBlank() ? "No messages after since=" + sinceEpochMillis : sample,
                ".txt");
        throw new AssertionError("Не найден Kafka-сигнал загрузки splitter config"
                + "\nTopic=" + topic
                + "\nEnv=" + kafkaEnv()
                + "\nmessageId/requestId=" + messageKey
                + "\nSince=" + sinceEpochMillis
                + "\nObserved sample:\n" + sample);
    }

    private static Optional<KafkaLoadOutcome> outcomeFromNode(JsonNode node,
                                                             Payload payload,
                                                             String messageKey,
                                                             boolean badRequestByRequestContract) {
        if (isStatusMessage(node, payload, messageKey)) {
            String status = normalizedText(node, "status");
            if ("CONFIG_LOADED".equals(status)) {
                return Optional.of(KafkaLoadOutcome.loaded(payload, messageKey, node));
            }
            if ("CONFIG_NOT_LOADED".equals(status)) {
                return Optional.of(KafkaLoadOutcome.rejected(payload, messageKey, node,
                        badRequestByRequestContract, "CONFIG_ERROR"));
            }
        }

        if (!isMonitoringMessage(node, payload, messageKey)) {
            return Optional.empty();
        }

        String result = normalizedText(node, "result");
        if ("LOADED".equals(result) || "LOADED_WITH_PRECALC".equals(result)) {
            return Optional.of(KafkaLoadOutcome.loaded(payload, messageKey, node));
        }
        if ("NOT_LOADED_OLD_VERSION".equals(result)) {
            return Optional.of(KafkaLoadOutcome.rejected(payload, messageKey, node,
                    false, "OLD_VERSION"));
        }
        if ("REQUEST_PARAMS_WITH_PRECALC_ENABLED".equals(result)) {
            return Optional.of(KafkaLoadOutcome.rejected(payload, messageKey, node,
                    false, "REQUEST_PARAMS_WITH_PRECALC_NOT_SUPPORTED"));
        }
        if ("VALIDATION_FAILED".equals(result)) {
            return Optional.of(KafkaLoadOutcome.rejected(payload, messageKey, node,
                    badRequestByRequestContract, "CONFIG_ERROR"));
        }
        return Optional.empty();
    }

    private static boolean isStatusMessage(JsonNode node, Payload payload, String messageKey) {
        return "STATUS".equals(normalizedText(node, "messageType"))
                && messageIdMatches(node, payload, messageKey);
    }

    private static boolean isMonitoringMessage(JsonNode node, Payload payload, String messageKey) {
        return "SPLITTING_CONFIG_LOAD".equals(normalizedText(node, "function"))
                && messageIdMatches(node, payload, messageKey);
    }

    private static boolean messageIdMatches(JsonNode node, Payload payload, String messageKey) {
        List<String> candidates = List.of(
                Objects.toString(messageKey, ""),
                Objects.toString(text(payload.root(), "messageId"), ""),
                Objects.toString(text(payload.root(), "requestId"), "")
        );
        return candidates.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(candidate -> Objects.equals(candidate, text(node, "messageId"))
                        || Objects.equals(candidate, text(node, "requestId"))
                        || Objects.equals(candidate, text(node, "requestIdIn"))
                        || Objects.equals(candidate, text(node, "configMessageId")));
    }

    private static void sendJson(String key, String payload) {
        String env = kafkaEnv();
        String topic = inputTopic();
        Duration timeout = timeout();

        Allure.parameter("splitter.config.kafka.env", env);
        Allure.parameter("splitter.config.kafka.inputTopic", topic);

        Properties properties = producerProperties(env);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>(topic, key, payload))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            producer.flush();
        } catch (Exception exception) {
            throw new AssertionError("Не удалось отправить splitter config в Kafka topic=" + topic
                    + ", env=" + env
                    + ". Проверь kafka_producer." + env + ".bootstrap.servers"
                    + " или передай -Dkafka_producer." + env + ".bootstrap.servers", exception);
        }
    }

    private static void assignAtEnd(KafkaConsumer<String, String> consumer, String topic) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(200));
        }

        Set<TopicPartition> assignment = consumer.assignment();
        if (assignment.isEmpty()) {
            throw new AssertionError("Kafka consumer не получил partition assignment для topic=" + topic
                    + ", env=" + kafkaEnv());
        }
        consumer.seekToEnd(assignment);
        assignment.forEach(consumer::position);
    }

    private static Response response(KafkaLoadOutcome outcome) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("responseId", firstNonBlank(outcome.requestId(), outcome.messageId()));
        body.put("result", outcome.restResult());
        body.put("loadMethod", "KAFKA");
        body.put("configLoadStatus", outcome.configStatus());
        putNullable(body, "resultDetails", outcome.resultDetails());
        putNullable(body, "message", outcome.message());
        putNullable(body, "messageId", outcome.messageId());
        putNullable(body, "requestId", outcome.requestId());
        putNullable(body, "splittingPointCode", outcome.splittingPointCode());
        putLongNullable(body, "currentConfigVersion", outcome.currentConfigVersion());
        putLongNullable(body, "newConfigVersion", outcome.newConfigVersion());
        putLongNullable(body, "configVersion", outcome.newConfigVersion());

        String json = pretty(body);
        Allure.addAttachment("Synthetic splitter config load response", "application/json", json, ".json");
        return new ResponseBuilder()
                .setStatusCode(outcome.httpStatusCode())
                .setContentType(ContentType.JSON)
                .setBody(json)
                .build();
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void putLongNullable(ObjectNode node, String field, Long value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static String kafkaEnv() {
        return SplitterConfigProperties.string("splitter.config.kafka.env", TEST_CONFIG.env());
    }

    private static String inputTopic() {
        return SplitterConfigProperties.string("splitter.config.kafka.input.topic", DEFAULT_INPUT_TOPIC);
    }

    private static String statusTopic() {
        return SplitterConfigProperties.string("splitter.config.kafka.status.topic", DEFAULT_STATUS_TOPIC);
    }

    private static String monitoringTopic() {
        return SplitterConfigProperties.string("splitter.config.kafka.monitoring.topic", DEFAULT_MONITORING_TOPIC);
    }

    private static boolean statusRequired() {
        return SplitterConfigProperties.bool("splitter.config.kafka.status.required", true);
    }

    private static Duration timeout() {
        return SplitterConfigProperties.durationSeconds("splitter.config.kafka.timeout.seconds", DEFAULT_TIMEOUT);
    }

    private static Properties producerProperties(String env) {
        Properties source = classpathProperties("kafka-producers.properties");
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

    private static Properties consumerProperties(String env) {
        Properties source = classpathProperties("kafka-consumers.properties");
        Properties target = new Properties();
        applyPrefix(source, target, "kafka_consumer.all.");
        applyPrefix(source, target, "kafka_consumer." + env + ".");
        applySystemPrefix(target, "kafka_consumer.all.");
        applySystemPrefix(target, "kafka_consumer." + env + ".");

        target.remove("key.serializer");
        target.remove("value.serializer");
        target.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        target.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        target.put(ConsumerConfig.GROUP_ID_CONFIG, "splitter-config-load-" + UUID.randomUUID());
        target.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        target.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        target.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "300");
        target.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");

        Object bootstrapServers = target.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);
        if (bootstrapServers == null || String.valueOf(bootstrapServers).isBlank()) {
            throw new AssertionError("Не заданы bootstrap.servers для Kafka consumer env=" + env
                    + ". Добавь kafka_consumer." + env + ".bootstrap.servers в kafka-consumers.properties"
                    + " или передай -Dkafka_consumer." + env + ".bootstrap.servers=<hosts>");
        }
        return target;
    }

    private static Properties classpathProperties(String resource) {
        Properties properties = new Properties();
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать " + resource, exception);
        }
        return properties;
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
        if (object instanceof String) {
            return (String) object;
        }
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать splitter config в JSON", exception);
        }
    }

    private static Optional<JsonNode> parseJson(String raw) {
        try {
            return Optional.of(OBJECT_MAPPER.readTree(raw));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static List<JsonNode> extractObjectNodes(String payload) {
        List<JsonNode> result = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            collectCandidateJsonRoots(root).forEach(candidate -> collectObjectNodes(candidate, result));
        } catch (JsonProcessingException ignored) {
            // Broken Kafka payloads are ignored while polling for the matching service signal.
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

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String normalizedText(JsonNode node, String field) {
        String raw = text(node, field);
        return raw == null ? null : raw.trim().replaceFirst("^'+", "");
    }

    private static Long longAny(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node == null ? null : node.path(field);
            if (value != null && value.canConvertToLong()) {
                return value.asLong();
            }
            String raw = text(node, field);
            if (raw != null && !raw.isBlank()) {
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException ignored) {
                    // try next field
                }
            }
        }
        return null;
    }

    private static String stringAny(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String messageIdFromRaw(String raw) {
        Matcher matcher = MESSAGE_ID_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String pretty(JsonNode node) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            return String.valueOf(node);
        }
    }

    private static String unescapeUnicode(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ) {
            char c = value.charAt(i);
            if (c == '\\' && i + 5 < value.length() && value.charAt(i + 1) == 'u') {
                String hex = value.substring(i + 2, i + 6);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {
                    // fallback below
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private record Payload(String raw, JsonNode root, boolean malformedJson) {
    }

    private record KafkaLoadOutcome(Payload request,
                                    String messageId,
                                    String requestId,
                                    String restResult,
                                    String configStatus,
                                    String resultDetails,
                                    String message,
                                    String splittingPointCode,
                                    Long currentConfigVersion,
                                    Long newConfigVersion,
                                    int httpStatusCode,
                                    JsonNode sourceNode) {

        static KafkaLoadOutcome loaded(Payload request, String messageKey, JsonNode sourceNode) {
            Long newVersion = firstLong(longAny(sourceNode, "newConfigVersion", "configVersion"),
                    longAny(request.root(), "configVersion"));
            Long currentVersion = firstLong(longAny(sourceNode, "currentConfigVersion"), newVersion);
            return new KafkaLoadOutcome(request,
                    firstNonBlank(text(request.root(), "messageId"), messageKey),
                    firstNonBlank(text(request.root(), "requestId"), text(sourceNode, "requestIdIn"), messageKey),
                    "LOADED",
                    "CONFIG_LOADED",
                    stringAny(sourceNode, "resultDetails", "message"),
                    stringAny(sourceNode, "message", "resultDetails"),
                    firstNonBlank(text(request.root(), "splittingPointCode"), text(sourceNode, "splittingPointCode")),
                    currentVersion,
                    newVersion,
                    200,
                    sourceNode);
        }

        static KafkaLoadOutcome rejected(Payload request,
                                         String messageKey,
                                         JsonNode sourceNode,
                                         boolean badRequest,
                                         String restResult) {
            Long newVersion = firstLong(longAny(sourceNode, "newConfigVersion", "configVersion"),
                    longAny(request.root(), "configVersion"));
            Long currentVersion = firstLong(longAny(sourceNode, "currentConfigVersion"), newVersion);
            String details = stringAny(sourceNode, "resultDetails", "message", "errorMessage", "exception");
            return new KafkaLoadOutcome(request,
                    firstNonBlank(text(request.root(), "messageId"), messageKey),
                    firstNonBlank(text(request.root(), "requestId"), text(sourceNode, "requestIdIn"), messageKey),
                    restResult,
                    "CONFIG_NOT_LOADED",
                    details,
                    stringAny(sourceNode, "message", "resultDetails", "errorMessage", "exception"),
                    firstNonBlank(text(request.root(), "splittingPointCode"), text(sourceNode, "splittingPointCode")),
                    currentVersion,
                    newVersion,
                    badRequest ? 400 : 200,
                    sourceNode);
        }

        static KafkaLoadOutcome badRequest(Payload request, String messageKey, String details) {
            ObjectNode source = OBJECT_MAPPER.createObjectNode();
            source.put("result", "VALIDATION_FAILED");
            source.put("message", details);
            return new KafkaLoadOutcome(request,
                    messageKey,
                    messageKey,
                    "CONFIG_ERROR",
                    "CONFIG_NOT_LOADED",
                    details,
                    details,
                    text(request.root(), "splittingPointCode"),
                    longAny(request.root(), "configVersion"),
                    longAny(request.root(), "configVersion"),
                    400,
                    source);
        }

        static KafkaLoadOutcome endpointMismatch(Payload request, String messageKey, String endpointSplittingPointCode) {
            ObjectNode source = OBJECT_MAPPER.createObjectNode();
            source.put("result", "IGNORED_BY_ENDPOINT_SPLITTING_POINT");
            source.put("message", "Kafka payload sent; endpoint " + endpointSplittingPointCode
                    + " does not wait for foreign splittingPointCode=" + text(request.root(), "splittingPointCode"));
            return new KafkaLoadOutcome(request,
                    firstNonBlank(text(request.root(), "messageId"), messageKey),
                    firstNonBlank(text(request.root(), "requestId"), messageKey),
                    "IGNORED",
                    "CONFIG_NOT_LOADED",
                    text(source, "message"),
                    text(source, "message"),
                    text(request.root(), "splittingPointCode"),
                    longAny(request.root(), "configVersion"),
                    longAny(request.root(), "configVersion"),
                    200,
                    source);
        }

        private static Long firstLong(Long... values) {
            for (Long value : values) {
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }
}

package ru.sber.qa.splitter.EXPLAB_2603;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.splitter.split.SplitRequestDto;
import io.qameta.allure.Allure;
import ru.sber.qa.services.kafka.KafkaService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertTrue;
import static util.TestAssertions.fail;

/**
 * Kafka-проверки для КАП/reporting результата Сплиттера.
 * Ищет одно сообщение по requestId и проверяет структуру payload, а не только наличие подстрок.
 */
final class SplitterKapKafkaAssertions {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TS_AT = Pattern.compile("@timestamp\"\\s*:\\s*(\\d{10,})(?:\\.\\d+)?");
    private static final Pattern TS_PLAIN = Pattern.compile("\"timestamp\"\\s*:\\s*(\\d{10,})(?:\\.\\d+)?");

    private SplitterKapKafkaAssertions() {
    }

    static String findKapPayloadByRequestId(String envName,
                                            String topic,
                                            String requestId,
                                            long sinceEpochMillis,
                                            KafkaService kafkaService,
                                            Duration timeout) {
        Objects.requireNonNull(envName, "envName");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(kafkaService, "kafkaService");
        Objects.requireNonNull(timeout, "timeout");

        var consumer = kafkaService.consumerClient(envName, timeout);
        List<String> messagesSince = new ArrayList<>();
        try {
            consumer.subscribe(topic);
            consumer.poll(Duration.ofMillis(300));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(300));
                try {
                    consumer.records().forEach(recordWrapper -> {
                        var record = recordWrapper.toConsumerRecord();
                        Object raw = record.value();
                        if (raw == null) {
                            return;
                        }

                        String payload = unescapeUnicode(String.valueOf(raw));
                        long jsonTs = extractEpochMillisFromJson(payload, 0L);
                        long eventTs = jsonTs != 0L ? jsonTs : record.timestamp();
                        if (eventTs < sinceEpochMillis) {
                            return;
                        }
                        messagesSince.add(payload);
                        if (payload.contains(requestId)) {
                            throw new FoundKapPayloadSignal(payload);
                        }
                    });
                } catch (FoundKapPayloadSignal signal) {
                    attachJsonOrText("КАП Kafka payload / requestId=" + requestId, signal.payload());
                    return signal.payload();
                }
            }
        } finally {
            try {
                consumer.unsubscribe();
            } catch (Throwable ignored) {
            }
        }

        String sample = messagesSince.stream()
                .limit(3)
                .collect(Collectors.joining("\n---\n"));
        throw new AssertionError("Не найдено КАП-сообщение Сплиттера по requestId=" + requestId
                + " в Kafka env=" + envName
                + ", topic=" + topic
                + ", since=" + sinceEpochMillis
                + (sample.isBlank() ? "\nСообщений за окно ожидания нет."
                : "\nПервые сообщения за окно ожидания:\n" + sample));
    }

    static void assertKapPayloadHasFullResult(String payload,
                                              SplitRequestDto request,
                                              String... expectedObjectIds) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(expectedObjectIds, "expectedObjectIds");

        // Быстрый диагностический слой: если парсинг упадет, в сообщении будет видно, чего не хватает.
        assertPayloadContains(payload, request.getRequestId(), request.getSplittingId());
        assertPayloadContains(payload, expectedObjectIds);

        JsonNode root = readJsonPayload(payload);
        JsonNode businessResult = findBusinessResultNode(root);

        assertAnyFieldEquals(root, "requestId", request.getRequestId(), "КАП payload должен содержать requestId split-запроса");
        assertEquals(request.getRequestId(), businessResult.path("requestId").asText(null),
                "В business-result КАП payload requestId должен совпадать с исходным split-запросом\n" + payload);
        assertEquals(request.getSplittingId(), businessResult.path("splittingId").asText(null),
                "В business-result КАП payload splittingId должен совпадать с исходным split-запросом\n" + payload);
        assertTrue(businessResult.hasNonNull("splittingConfigVersion"),
                "КАП business-result должен содержать splittingConfigVersion\n" + payload);

        assertOptionalEnvelopeField(root, "messageId");
        assertOptionalEnvelopeField(root, "messageCreateTS");
        assertOptionalEnvelopeField(root, "splittingPoint");
        assertOptionalSourceIsSplitter(root);

        JsonNode splittingResults = businessResult.path("splittingResults");
        assertTrue(splittingResults.isArray(), "КАП business-result должен содержать массив splittingResults\n" + payload);

        List<String> actualObjectIds = new ArrayList<>();
        splittingResults.forEach(node -> actualObjectIds.add(node.path("objectId").asText(null)));

        List<String> expected = Arrays.asList(expectedObjectIds);
        assertEquals(expected.size(), actualObjectIds.size(),
                "Количество объектов в КАП payload должно совпадать с количеством объектов исходного split-запроса\n"
                        + "expected=" + expected + "\nactual=" + actualObjectIds + "\n" + payload);
        assertEquals(new LinkedHashSet<>(expected).size(), expected.size(),
                "В ожидаемом наборе objectId не должно быть дублей: " + expected);
        assertEquals(new LinkedHashSet<>(actualObjectIds).size(), actualObjectIds.size(),
                "В КАП payload не должно быть дублей objectId: " + actualObjectIds + "\n" + payload);
        assertEquals(new LinkedHashSet<>(expected), new LinkedHashSet<>(actualObjectIds),
                "КАП payload должен содержать ровно исходный набор objectId независимо от фильтрации API-ответа\n"
                        + "expected=" + expected + "\nactual=" + actualObjectIds + "\n" + payload);
    }

    static void assertNoNotSentMonitoringEvent(String envName,
                                               String topic,
                                               String requestId,
                                               long sinceEpochMillis,
                                               KafkaService kafkaService,
                                               Duration timeout) {
        Objects.requireNonNull(envName, "envName");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(kafkaService, "kafkaService");
        Objects.requireNonNull(timeout, "timeout");

        var consumer = kafkaService.consumerClient(envName, timeout);
        List<String> suspiciousMessages = new ArrayList<>();
        try {
            consumer.subscribe(topic);
            consumer.poll(Duration.ofMillis(300));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(300));
                consumer.records().forEach(recordWrapper -> {
                    var record = recordWrapper.toConsumerRecord();
                    Object raw = record.value();
                    if (raw == null) {
                        return;
                    }

                    String payload = unescapeUnicode(String.valueOf(raw));
                    long jsonTs = extractEpochMillisFromJson(payload, 0L);
                    long eventTs = jsonTs != 0L ? jsonTs : record.timestamp();
                    if (eventTs < sinceEpochMillis || !payload.contains(requestId)) {
                        return;
                    }

                    if (payload.contains("SPLITTING_RESULT_REPORT") && payload.contains("NOT_SENT")) {
                        suspiciousMessages.add(payload);
                    }
                });
            }
        } finally {
            try {
                consumer.unsubscribe();
            } catch (Throwable ignored) {
            }
        }

        if (!suspiciousMessages.isEmpty()) {
            String sample = suspiciousMessages.stream().limit(3).collect(Collectors.joining("\n---\n"));
            attachJsonOrText("Monitoring NOT_SENT payload / requestId=" + requestId, sample);
            fail("В monitoring topic найдено событие ошибки отправки результата в КАП: requestId=" + requestId
                    + ", env=" + envName
                    + ", topic=" + topic
                    + "\n" + sample);
        }
    }

    private static void assertPayloadContains(String payload, String... expectedSubstrings) {
        List<String> missing = Arrays.stream(expectedSubstrings)
                .filter(expected -> !payload.contains(expected))
                .toList();
        assertTrue(missing.isEmpty(), "Payload не содержит ожидаемые значения: " + missing + "\nPayload:\n" + payload);
    }

    private static JsonNode readJsonPayload(String payload) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            if (root.isTextual()) {
                return OBJECT_MAPPER.readTree(root.asText());
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new AssertionError("КАП payload должен быть JSON или JSON-строкой\n" + payload, exception);
        }
    }

    private static JsonNode findBusinessResultNode(JsonNode root) {
        JsonNode message = root.path("message");
        if (message.isObject() && message.has("splittingResults")) {
            return message;
        }
        if (message.isTextual()) {
            try {
                JsonNode parsedMessage = OBJECT_MAPPER.readTree(message.asText());
                if (parsedMessage.has("splittingResults")) {
                    return parsedMessage;
                }
            } catch (JsonProcessingException exception) {
                throw new AssertionError("Поле message в КАП payload должно быть JSON-объектом или JSON-строкой\n" + root, exception);
            }
        }
        if (root.has("splittingResults")) {
            return root;
        }

        JsonNode recursive = findFirstNodeWithField(root, "splittingResults");
        if (recursive != null) {
            return recursive;
        }

        throw new AssertionError("Не найден business-result со splittingResults в КАП payload\n" + root);
    }

    private static JsonNode findFirstNodeWithField(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.has(fieldName)) {
            return node;
        }
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findFirstNodeWithField(child, fieldName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void assertAnyFieldEquals(JsonNode root, String fieldName, String expectedValue, String message) {
        List<String> actualValues = new ArrayList<>();
        collectFieldValues(root, fieldName, actualValues);
        assertTrue(actualValues.contains(expectedValue), message + "\nexpected=" + expectedValue + "\nactual=" + actualValues + "\n" + root);
    }

    private static void collectFieldValues(JsonNode node, String fieldName, List<String> result) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                result.add(value.asText());
            }
            node.fields().forEachRemaining(entry -> collectFieldValues(entry.getValue(), fieldName, result));
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldValues(child, fieldName, result));
        }
    }

    private static void assertOptionalEnvelopeField(JsonNode root, String fieldName) {
        JsonNode value = root.path(fieldName);
        if (!value.isMissingNode()) {
            assertFalse(value.isNull() || value.asText().isBlank(),
                    "Если поле " + fieldName + " есть в envelope КАП payload, оно не должно быть пустым\n" + root);
        }
    }

    private static void assertOptionalSourceIsSplitter(JsonNode root) {
        JsonNode source = root.path("source");
        if (!source.isMissingNode()) {
            assertEquals("SPLITTER", source.asText(null),
                    "Если envelope КАП payload содержит source, он должен быть SPLITTER\n" + root);
        }
    }

    private static long extractEpochMillisFromJson(String json, long defaultTs) {
        Matcher m1 = TS_AT.matcher(json);
        if (m1.find()) {
            long value = parseLongOrDefault(m1.group(1), defaultTs);
            return value < 10_000_000_000L ? value * 1000L : value;
        }

        Matcher m2 = TS_PLAIN.matcher(json);
        if (m2.find()) {
            long value = parseLongOrDefault(m2.group(1), defaultTs);
            return value < 10_000_000_000L ? value * 1000L : value;
        }

        return defaultTs;
    }

    private static long parseLongOrDefault(String raw, long defaultValue) {
        try {
            return Long.parseLong(raw);
        } catch (RuntimeException exception) {
            return defaultValue;
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
                    // fall through
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static void attachJsonOrText(String name, String payload) {
        try {
            JsonNode root = readJsonPayload(payload);
            Allure.addAttachment(name, "application/json", OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root), ".json");
        } catch (RuntimeException | JsonProcessingException exception) {
            Allure.addAttachment(name, "text/plain", payload, ".txt");
        }
    }

    private static final class FoundKapPayloadSignal extends RuntimeException {
        private final String payload;

        private FoundKapPayloadSignal(String payload) {
            this.payload = payload;
        }

        private String payload() {
            return payload;
        }
    }
}



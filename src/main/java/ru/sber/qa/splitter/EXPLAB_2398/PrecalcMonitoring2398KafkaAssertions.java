package ru.sber.qa.splitter.EXPLAB_2398;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import ru.sber.qa.services.kafka.KafkaService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertTrue;
import static util.TestAssertions.fail;

/**
 * Kafka assertions для EXPLAB-2398.
 *
 * Ищем событие мониторинга по requestIdIn и проверяем JSON-контракт, описанный в постановке:
 * function=PRE_CALC_REQUEST, result, requestIdIn, splitting point, soConfigVersion, counters, service.
 */
final class PrecalcMonitoring2398KafkaAssertions {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TS_AT = Pattern.compile("@timestamp\"\\s*:\\s*(\\d{10,})(?:\\.\\d+)?");
    private static final Pattern TS_PLAIN = Pattern.compile("\"timestamp\"\\s*:\\s*(\\d{10,})(?:\\.\\d+)?");
    private static final Pattern TS_COMPLETED = Pattern.compile("\"completedTimestamp\"\\s*:\\s*\"?(\\d{10,})\"?");

    private PrecalcMonitoring2398KafkaAssertions() {
    }

    static void assertPrecalcMonitoringEvent(KafkaService kafkaService,
                                             String envName,
                                             String topic,
                                             long sinceEpochMillis,
                                             Duration timeout,
                                             PrecalcMonitoring2398EventExpectation expectation) {
        Objects.requireNonNull(kafkaService, "kafkaService");
        Objects.requireNonNull(envName, "envName");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(expectation, "expectation");

        String payload = findPrecalcPayloadByRequestId(
                kafkaService,
                envName,
                topic,
                expectation.requestId(),
                sinceEpochMillis,
                timeout);
        JsonNode event = monitoringEventNode(payload, expectation.requestId());
        attachJsonOrText("EXPLAB-2398 monitoring event / requestId=" + expectation.requestId(), event.toString());

        assertEquals("PRE_CALC_REQUEST", text(event, "function"), "Некорректный function в monitoring event\n" + event);
        assertEquals(expectation.result(), text(event, "result"), "Некорректный result в monitoring event\n" + event);
        assertEquals(expectation.requestId(), text(event, "requestIdIn"), "Некорректный requestIdIn в monitoring event\n" + event);
        assertEquals(expectation.soConfigVersion(), number(event, "soConfigVersion"), "Некорректный soConfigVersion\n" + event);
        assertEquals("splitter--service", text(event, "service"), "Некорректный service в monitoring event\n" + event);

        assertPrecalcSplittingPoint(event);
        assertCompletedTimestamp(event, sinceEpochMillis);
        assertMessageIsNotBlank(event);

        expectation.counters().forEach((field, expectedValue) ->
                assertEquals(expectedValue.longValue(), number(event, field), "Некорректный счетчик " + field + "\n" + event));
    }

    private static String findPrecalcPayloadByRequestId(KafkaService kafkaService,
                                                        String envName,
                                                        String topic,
                                                        String requestId,
                                                        long sinceEpochMillis,
                                                        Duration timeout) {
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
                        if (payload.contains(requestId) && payload.contains("PRE_CALC_REQUEST")) {
                            throw new FoundPrecalcPayloadSignal(payload);
                        }
                    });
                } catch (FoundPrecalcPayloadSignal signal) {
                    attachJsonOrText("EXPLAB-2398 raw monitoring payload / requestId=" + requestId, signal.payload());
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
                .limit(5)
                .collect(Collectors.joining("\n---\n"));
        throw new AssertionError("Не найдено monitoring-сообщение pre-calculate по requestId=" + requestId
                + " в Kafka env=" + envName
                + ", topic=" + topic
                + ", since=" + sinceEpochMillis
                + (sample.isBlank() ? "\nСообщений за окно ожидания нет."
                : "\nПервые сообщения за окно ожидания:\n" + sample));
    }

    private static JsonNode monitoringEventNode(String payload, String requestId) {
        JsonNode root = readJsonPayload(payload);
        JsonNode direct = findPrecalcEvent(root, requestId);
        if (direct != null) {
            return direct;
        }
        fail("Не найден JSON-объект monitoring event с function=PRE_CALC_REQUEST и requestIdIn=" + requestId + "\nPayload:\n" + payload);
        return root;
    }

    private static JsonNode findPrecalcEvent(JsonNode node, String requestId) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()
                && "PRE_CALC_REQUEST".equals(node.path("function").asText(null))
                && requestId.equals(node.path("requestIdIn").asText(null))) {
            return node;
        }

        JsonNode message = node.path("message");
        if (message.isObject()) {
            JsonNode found = findPrecalcEvent(message, requestId);
            if (found != null) {
                return found;
            }
        }
        if (message.isTextual()) {
            JsonNode parsed = tryReadJson(message.asText());
            JsonNode found = findPrecalcEvent(parsed, requestId);
            if (found != null) {
                return found;
            }
        }

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                JsonNode found = findPrecalcEvent(fields.next().getValue(), requestId);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findPrecalcEvent(child, requestId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JsonNode readJsonPayload(String payload) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            if (root.isTextual()) {
                return OBJECT_MAPPER.readTree(root.asText());
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Monitoring payload должен быть JSON или JSON-строкой\n" + payload, exception);
        }
    }

    private static JsonNode tryReadJson(String raw) {
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static void assertPrecalcSplittingPoint(JsonNode event) {
        List<String> fieldCandidates = List.of("splittingPoing", "splittingPoin", "splittingPoint", "splittingPointCode");
        boolean mapper = fieldCandidates.stream().anyMatch(field -> "MAPPER".equals(text(event, field)));
        assertTrue(mapper, "Monitoring event должен содержать точку сплиттования MAPPER в одном из полей "
                + fieldCandidates + "\n" + event);
    }

    private static void assertCompletedTimestamp(JsonNode event, long sinceEpochMillis) {
        assertFalse(event.path("completedTimestamp").isMissingNode(),
                "Monitoring event должен содержать completedTimestamp\n" + event);
        long raw = number(event, "completedTimestamp");
        long millis = raw < 10_000_000_000L ? raw * 1000L : raw;
        assertTrue(millis >= sinceEpochMillis - 1000L,
                "completedTimestamp должен быть не раньше начала проверяемого действия"
                        + "\ncompletedTimestamp=" + raw
                        + "\nsince=" + sinceEpochMillis
                        + "\n" + event);
    }

    private static void assertMessageIsNotBlank(JsonNode event) {
        String message = text(event, "message");
        assertFalse(message == null || message.isBlank(), "Monitoring event должен содержать непустой message\n" + event);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        assertFalse(value.isMissingNode() || value.isNull(), "Поле " + field + " отсутствует или null\n" + node);
        if (value.isNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText());
        } catch (RuntimeException exception) {
            throw new AssertionError("Поле " + field + " должно быть числом или числовой строкой\n" + node, exception);
        }
    }

    private static long extractEpochMillisFromJson(String json, long defaultTs) {
        Matcher completed = TS_COMPLETED.matcher(json);
        if (completed.find()) {
            return normalizeEpochMillis(parseLongOrDefault(completed.group(1), defaultTs));
        }
        Matcher m1 = TS_AT.matcher(json);
        if (m1.find()) {
            return normalizeEpochMillis(parseLongOrDefault(m1.group(1), defaultTs));
        }
        Matcher m2 = TS_PLAIN.matcher(json);
        if (m2.find()) {
            return normalizeEpochMillis(parseLongOrDefault(m2.group(1), defaultTs));
        }
        return defaultTs;
    }

    private static long normalizeEpochMillis(long value) {
        return value < 10_000_000_000L ? value * 1000L : value;
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

    private static final class FoundPrecalcPayloadSignal extends RuntimeException {
        private final String payload;

        private FoundPrecalcPayloadSignal(String payload) {
            this.payload = payload;
        }

        private String payload() {
            return payload;
        }
    }
}



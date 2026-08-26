package ru.sber.qa.splitter.EXPLAB_2400;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.splitter.config.LoadConfigResponseDto;
import dto.splitter.monitoring.SplitterConfigLoadMonitoringDto;
import io.qameta.allure.Allure;
import ru.sber.qa.services.kafka.KafkaService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static util.TestAssertions.fail;

/**
 * Flow чтения технического monitoring topic для EXPLAB-2400.
 */
final class SplitterConfigLoadMonitoring2400Flow {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_MONITORING_TOPIC = "omon_explab_splitter_log";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45);

    ConfigLoadMonitoringCapture2400 captureConfigLoadEvent(KafkaService kafkaService,
                                                            String messageId,
                                                            String expectedResult,
                                                            Supplier<LoadConfigResponseDto> loadAction) {
        Objects.requireNonNull(kafkaService, "kafkaService");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(expectedResult, "expectedResult");
        Objects.requireNonNull(loadAction, "loadAction");

        Predicate<JsonNode> predicate = node ->
                "SPLITTING_CONFIG_LOAD".equals(normalizedText(node, "function"))
                        && expectedResult.equals(normalizedText(node, "result"))
                        && messageIdMatches(node, messageId);

        String env = kafkaEnv();
        String topic = monitoringTopic();
        Duration timeout = timeout();

        Allure.parameter("splitter.config.load.monitoring.kafka.env", env);
        Allure.parameter("splitter.config.load.monitoring.topic", topic);
        Allure.parameter("splitter.config.load.monitoring.timeout", timeout.toString());

        var consumer = kafkaService.consumerClient(env, timeout);
        List<String> observedPayloads = new ArrayList<>();
        long startedAt = 0L;
        LoadConfigResponseDto response = null;
        try {
            // Подписываемся до REST-вызова, чтобы auto.offset.reset=latest не потерял быстрое событие.
            consumer.subscribe(topic);
            consumer.poll(Duration.ofMillis(300));

            startedAt = System.currentTimeMillis();
            Allure.parameter("splitter.config.load.monitoring.since", String.valueOf(startedAt));
            response = loadAction.get();

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(300));
                List<JsonNode> matched = new ArrayList<>();
                long eventWindowStart = startedAt;
                consumer.records().forEach(recordWrapper -> {
                    var record = recordWrapper.toConsumerRecord();
                    Object raw = record.value();
                    if (raw == null) {
                        return;
                    }
                    if (record.timestamp() > 0 && record.timestamp() < eventWindowStart) {
                        return;
                    }

                    String payload = unescapeUnicode(String.valueOf(raw));
                    observedPayloads.add(payload);
                    extractObjectNodes(payload).stream()
                            .filter(predicate)
                            .forEach(matched::add);
                });

                if (!matched.isEmpty()) {
                    JsonNode eventNode = matched.get(0);
                    Allure.addAttachment("EXPLAB-2400 matched monitoring event",
                            "application/json",
                            pretty(eventNode),
                            ".json");
                    SplitterConfigLoadMonitoringDto event = toMonitoringDto(eventNode);
                    return new ConfigLoadMonitoringCapture2400(response, event, startedAt);
                }
            }
        } finally {
            try {
                consumer.unsubscribe();
            } catch (Throwable ignored) {
                // best effort cleanup
            }
        }

        String sample = observedPayloads.stream()
                .limit(10)
                .collect(Collectors.joining("\n---\n"));
        Allure.addAttachment("EXPLAB-2400 observed monitoring sample",
                "text/plain",
                sample.isBlank() ? "No messages after since=" + startedAt : sample,
                ".txt");
        fail("Не найдено monitoring-сообщение: function=SPLITTING_CONFIG_LOAD"
                + ", result=" + expectedResult
                + ", requestIdIn=" + messageId
                + "\nTopic=" + topic
                + "\nEnv=" + env
                + "\nSince=" + startedAt
                + "\nObserved sample:\n" + sample);
        return null;
    }

    private static SplitterConfigLoadMonitoringDto toMonitoringDto(JsonNode event) {
        try {
            return OBJECT_MAPPER.treeToValue(event, SplitterConfigLoadMonitoringDto.class);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Не удалось преобразовать monitoring event в DTO\n" + pretty(event), exception);
        }
    }

    private String kafkaEnv() {
        return System.getProperty("splitter.config.load.monitoring.kafka.env", TEST_CONFIG.env());
    }

    private String monitoringTopic() {
        return System.getProperty("splitter.config.load.monitoring.topic", DEFAULT_MONITORING_TOPIC);
    }

    private Duration timeout() {
        long seconds = Long.parseLong(System.getProperty(
                "splitter.config.load.monitoring.timeout.seconds",
                String.valueOf(DEFAULT_TIMEOUT.toSeconds())));
        return Duration.ofSeconds(seconds);
    }

    private static List<JsonNode> extractObjectNodes(String payload) {
        List<JsonNode> result = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            collectCandidateJsonRoots(root).forEach(candidate -> collectObjectNodes(candidate, result));
        } catch (JsonProcessingException ignored) {
            // Другие технические сообщения в топике не должны останавливать поиск.
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

    private static boolean messageIdMatches(JsonNode node, String messageId) {
        return Objects.equals(messageId, text(node, "requestIdIn"))
                || Objects.equals(messageId, text(node, "messageId"))
                || Objects.equals(messageId, text(node, "configMessageId"));
    }

    private static String normalizedText(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : value.trim().replaceFirst("^'+", "");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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
            char current = value.charAt(i);
            if (current == '\\' && i + 5 < value.length() && value.charAt(i + 1) == 'u') {
                String hex = value.substring(i + 2, i + 6);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) {
                    // fallback below
                }
            }
            out.append(current);
            i++;
        }
        return out.toString();
    }
}



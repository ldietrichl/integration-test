package util;

import org.jetbrains.annotations.NotNull;
import ru.sber.qa.services.kafka.KafkaService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Утилиты-ассёрты для проверки сообщений в Kafka.
 * <p>
 * Поддержано:
 * - фильтр по hostname (подстрока в json);
 * - фильтр по времени (sinceEpochMillis, поддерживаются поля "@timestamp" и "timestamp");
 * - распаковка unicode-последовательностей (unicode) в читаемый текст.
 */
public final class KafkaQueueAsserts {
    private KafkaQueueAsserts() {
    }

    // ================== Внутренние утилиты ==================

    /**
     * Регулярка для @timestamp (в секундах с долями или миллисекундах).
     */
    private static final Pattern TS_AT = Pattern.compile("@timestamp\"\\s*:\\s*(\\d{10,})(?:\\.\\d+)?");
    /**
     * Регулярка для "timestamp" (в секундах с долями или миллисекундах).
     */
    private static final Pattern TS_PLAIN = Pattern.compile("\"timestamp\"\\s*:\\s*(\\d{10,})(?:\\.\\d+)?");

    /**
     * Вытаскивает epoch millis из json. Если нашли секунды (10 знаков) – умножаем до миллисекунд.
     */
    private static long extractEpochMillisFromJson(@NotNull String json, long defaultTs) {
        Matcher m1 = TS_AT.matcher(json);
        if (m1.find()) {
            try {
                long v = Long.parseLong(m1.group(1));
                return (v < 10_000_000_000L) ? v * 1000L : v;
            } catch (Exception ignored) {
            }
        }

        Matcher m2 = TS_PLAIN.matcher(json);
        if (m2.find()) {
            try {
                long v = Long.parseLong(m2.group(1));
                return (v < 10_000_000_000L) ? v * 1000L : v;
            } catch (Exception ignored) {
            }
        }

        return defaultTs;
    }

    /**
     * Преобразует unicode-последовательности в читаемый текст.
     */
    private static String unescapeUnicode(@NotNull String s) {
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
                } catch (NumberFormatException ignore) { /* падаем в default-ветку */ }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    // ================== Ассерты ==================

    /**
     * Проверяет, что в указанном топике найдено хотя бы одно сообщение,
     * удовлетворяющее одновременно:
     * - json содержит подстроку hostnameSubstring;
     * - время события (из @timestamp/timestamp либо timestamp записи Kafka)
     * >= sinceEpochMillis;
     * - json содержит expectedSubstring.
     * <p>
     * При неуспехе печатает удобную выборку первых 3 подходящих по hostname+since сообщений
     * (с уже раскодированным unicode) для быстрой диагностики.
     */
    public static void assertFirstPayloadContainsByHostnameSince(
            @NotNull String envName,
            @NotNull String topic,
            @NotNull String expectedSubstring,
            @NotNull String hostnameSubstring,
            long sinceEpochMillis,
            @NotNull KafkaService kafkaService,
            @NotNull Duration timeout
    ) {
        Objects.requireNonNull(envName, "envName");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(expectedSubstring, "expectedSubstring");
        Objects.requireNonNull(hostnameSubstring, "hostnameSubstring");
        Objects.requireNonNull(kafkaService, "kafkaService");
        Objects.requireNonNull(timeout, "timeout");

        var consumer = kafkaService.consumerClient(envName, timeout);
        try {
            // subscribe + join group
            consumer.subscribe(topic);
            consumer.poll(Duration.ofMillis(300));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            List<String> matchedByHostAndTime = new ArrayList<>();

            try {
                while (System.currentTimeMillis() < deadline) {
                    consumer.poll(Duration.ofMillis(300));

                    consumer.records().forEach(vr -> {
                        var cr = vr.toConsumerRecord();
                        Object raw = cr.value();
                        if (raw == null) return;

                        String jsonRaw = String.valueOf(raw);
                        String json = unescapeUnicode(jsonRaw);

                        // фильтры hostname + since
                        boolean hostOk = json.contains(hostnameSubstring);

                        long jsonTs = extractEpochMillisFromJson(json, 0L);
                        long recordTs = cr.timestamp();
                        long eventTs = (jsonTs != 0L) ? jsonTs : recordTs;
                        boolean timeOk = eventTs >= sinceEpochMillis;

                        if (hostOk && timeOk) {
                            matchedByHostAndTime.add(json);

                            // целевое условие
                            if (json.contains(expectedSubstring)) {
                                // досрочно прекращаем поиск (break из forEach невозможен)
                                throw new FoundMessageSignal();
                            }
                        }
                    });
                }
            } catch (FoundMessageSignal ignore) {
                // нашли нужное сообщение – тест OK
                return;
            }

            // Не нашли нужное сообщение — готовим подробное сообщение об ошибке
            String sample = matchedByHostAndTime.stream()
                    .limit(3)
                    .collect(Collectors.joining("\n---\n"));

            String msg = "Kafka payload assert failed (first matched by hostname & since) "
                         + String.format("(env=%s, topic=%s, hostname~%s, since=%d)%n",
                    envName, topic, hostnameSubstring, sinceEpochMillis)
                         + "Expected substring: " + expectedSubstring + System.lineSeparator()
                         + (matchedByHostAndTime.isEmpty()
                    ? "No matching message found (by hostname & since)."
                    : "Collected messages (limited to 3):\n" + sample);

            throw new AssertionError(msg);
        } finally {
            try {
                consumer.unsubscribe();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void assertFirstPayload(
            @NotNull String envName,
            @NotNull String topic,
            @NotNull String expectedSubstring,
            long sinceEpochMillis,
            @NotNull KafkaService kafkaService,
            @NotNull Duration timeout
    ) {

        Objects.requireNonNull(envName, "envName");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(expectedSubstring, "expectedSubstring");
        Objects.requireNonNull(kafkaService, "kafkaService");
        Objects.requireNonNull(timeout, "timeout");

        var consumer = kafkaService.consumerClient(envName, timeout);
        try {
            // subscribe + join group
            consumer.subscribe(topic);
            consumer.poll(Duration.ofMillis(300));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            List<String> matchedByHostAndTime = new ArrayList<>();

            try {
                while (System.currentTimeMillis() < deadline) {
                    consumer.poll(Duration.ofMillis(300));

                    consumer.records().forEach(vr -> {
                        var cr = vr.toConsumerRecord();
                        Object raw = cr.value();
                        if (raw == null) return;

                        String jsonRaw = String.valueOf(raw);
                        String json = unescapeUnicode(jsonRaw);

                        // фильтры hostname + since

                        long jsonTs = extractEpochMillisFromJson(json, 0L);
                        long recordTs = cr.timestamp();
                        long eventTs = (jsonTs != 0L) ? jsonTs : recordTs;
                        boolean timeOk = eventTs >= sinceEpochMillis;

                        if ( timeOk) {
                            matchedByHostAndTime.add(json);

                            // целевое условие
                            if (json.contains(expectedSubstring)) {
                                // досрочно прекращаем поиск (break из forEach невозможен)
                                throw new FoundMessageSignal();
                            }
                        }
                    });
                }
            } catch (FoundMessageSignal ignore) {
                // нашли нужное сообщение – тест OK
                return;
            }

            // Не нашли нужное сообщение — готовим подробное сообщение об ошибке
            String sample = matchedByHostAndTime.stream()
                    .limit(3)
                    .collect(Collectors.joining("\n---\n"));

            String msg = "Kafka payload assert failed (first matched by hostname & since) "
                    + String.format("(env=%s, topic=%s,  since=%d)%n",
                    envName, topic,  sinceEpochMillis)
                    + "Expected substring: " + expectedSubstring + System.lineSeparator()
                    + (matchedByHostAndTime.isEmpty()
                    ? "No matching message found (by hostname & since)."
                    : "Collected messages (limited to 3):\n" + sample);

            throw new AssertionError(msg);
        } finally {
            try { consumer.unsubscribe(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Внутренний "сигнал" для досрочного выхода из forEach.
     */
    private static final class FoundMessageSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static void assertFirstPayloadContainsByHostnameSinceUni(
            @NotNull String envName,
            @NotNull String topic,
            @NotNull String expectedSubstring, // человекочитаемая строка (напр. русская)
            @NotNull String hostnameSubstring, // фильтр по hostname
            long sinceEpochMillis,
            @NotNull KafkaService kafkaService,
            @NotNull Duration timeout
    ) {
        var consumer = kafkaService.consumerClient(envName);
        try {
            consumer.subscribe(topic);
            consumer.poll(Duration.ofMillis(300));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            List<String> matched = new ArrayList<>();

            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(300));
                consumer.records().forEach(r -> {
                    var cr = r.toConsumerRecord();
                    Object val = cr.value();
                    if (val == null) return;

                    String jsonRaw = String.valueOf(val);
                    String json = unescapeUnicode(jsonRaw); // <<< ВАЖНО

                    boolean hostOk = json.contains(hostnameSubstring); // hostname ASCII — ок
                    long jsonMillis = extractEpochMillisFromJson(json, 0L);
                    long recordMillis = cr.timestamp();
                    long eventMillis = (jsonMillis > 0 ? jsonMillis : recordMillis);

                    if (hostOk && eventMillis >= sinceEpochMillis) {
                        matched.add(json); // сохраняем уже раскодированное
                    }
                });
            }

            if (matched.isEmpty()) {
                throw new AssertionError(String.format(
                        "Kafka payload assert failed: no messages found (env=%s, topic=%s, hostname~=%s, since=%d)",
                        envName, topic, hostnameSubstring, sinceEpochMillis
                ));
            }

            // сравнение по подстроке — уже по раскодированным строкам
            Optional<String> first = matched.stream()
                    .filter(m -> m.contains(expectedSubstring))
                    .findFirst();

            if (first.isEmpty()) {
                String sample = matched.stream()
                        .limit(3)
                        .collect(Collectors.joining("\n---\n"));
                throw new AssertionError(String.format(
                        "Kafka payload assert failed (contains check)\n" +
                        "Expected substring: %s\n" +
                        "No matching message found. Collected messages (limited to 3):\n%s\n" +
                        "(env=%s, topic=%s, hostname~=%s, since=%d)",
                        expectedSubstring, sample, envName, topic, hostnameSubstring, sinceEpochMillis
                ));
            }
        } finally {
            consumer.unsubscribe();
        }
    }
}

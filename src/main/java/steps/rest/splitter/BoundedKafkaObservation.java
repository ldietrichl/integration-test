package steps.rest.splitter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sber.qa.services.kafka.KafkaConsumerClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Bounds observation work independently of the traffic in the shared monitoring topic. */
final class BoundedKafkaObservation {
    static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    static final int MAX_SAMPLE_BYTES = 32768;
    static final int MAX_SAMPLE_RECORDS = 10;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder().streamReadConstraints(
            StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(MAX_PAYLOAD_BYTES)
                    .maxNumberLength(128).build()).build());
    private final StringBuilder sample = new StringBuilder();
    private int sampleBytes;
    private int samples;
    private long observed;
    private long oversized;

    static <T> T batch(KafkaConsumerClient<?, ?> consumer, Duration timeout, Supplier<T> inspect) {
        consumer.clear();
        try {
            consumer.poll(timeout);
            return inspect.get();
        } finally { consumer.clear(); } // Service owns/ closes the cached client after the test.
    }

    boolean record(String description, String value) {
        observed++;
        boolean allowed = withinLimit(value);
        if (!allowed) oversized++;
        if (samples < MAX_SAMPLE_RECORDS && sampleBytes < MAX_SAMPLE_BYTES) {
            // Substring before encoding: even a giant broker value cannot grow the diagnostic buffer.
            String boundedDescription = description == null ? "" : description.substring(0, Math.min(description.length(), 1024));
            String text = boundedDescription + "\n" + (allowed ? value.substring(0, Math.min(value.length(), 3000))
                    : "[payload exceeds " + MAX_PAYLOAD_BYTES + " bytes; not parsed]") + "\n---\n";
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            int end = Math.min(bytes.length, MAX_SAMPLE_BYTES - sampleBytes);
            // Keep a UTF-8 character whole when cutting the last sample.
            while (end > 0 && end < bytes.length && (bytes[end] & 0xC0) == 0x80) end--;
            sample.append(new String(bytes, 0, end, StandardCharsets.UTF_8));
            sampleBytes += end;
            samples++;
        }
        return allowed;
    }

    String summary() { return "observed=" + observed + "; oversized=" + oversized + "; sampleBytes=" + sampleBytes + "\n" + sample; }

    static boolean withinLimit(String value) {
        return value != null && value.length() <= MAX_PAYLOAD_BYTES
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_PAYLOAD_BYTES;
    }

    static Optional<JsonNode> parse(String value) {
        if (!withinLimit(value)) return Optional.empty();
        String trimmed = value.stripLeading();
        if (trimmed.isEmpty() || "{[\"".indexOf(trimmed.charAt(0)) < 0) return Optional.empty();
        try { return Optional.ofNullable(JSON.readTree(value)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { return Optional.empty(); }
    }

    static List<JsonNode> objects(String value) {
        List<JsonNode> result = new ArrayList<>();
        Optional<JsonNode> parsed = parse(value);
        if (parsed.isEmpty()) return result;
        JsonNode root = parsed.get();
        ArrayDeque<JsonNode> queue = new ArrayDeque<>();
        queue.add(root);
        if (root.isTextual()) parse(root.asText()).ifPresent(queue::add);
        else if (root.path("message").isTextual()) parse(root.path("message").asText()).ifPresent(queue::add);
        int visited = 0;
        while (!queue.isEmpty()) {
            JsonNode node = queue.removeFirst();
            if (++visited > 16384) return List.of(); // Reject the whole candidate, not a partially inspected match.
            if (node.isObject()) result.add(node);
            if (node.isContainerNode()) {
                if (queue.size() + node.size() > 16384) return List.of();
                // Preserve the original depth-first field order when several signals share one envelope.
                List<JsonNode> children = new ArrayList<>(node.size());
                node.forEach(children::add);
                for (int i = children.size() - 1; i >= 0; i--) queue.addFirst(children.get(i));
            }
        }
        return result;
    }
}

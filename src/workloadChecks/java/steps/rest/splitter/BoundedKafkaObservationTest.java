package steps.rest.splitter;

import org.junit.jupiter.api.Test;
import ru.sber.qa.services.kafka.KafkaConsumerClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoundedKafkaObservationTest {
    @Test void sampleStaysBoundedUnderHeavyTraffic() {
        var observation = new BoundedKafkaObservation();
        String value = "я".repeat(5000);
        for (int i = 0; i < 10000; i++) assertTrue(observation.record("record " + i, value));
        assertTrue(observation.summary().getBytes(StandardCharsets.UTF_8).length < 33000);
        assertTrue(observation.summary().contains("observed=10000"));
        assertFalse(observation.summary().contains("record 11"));
    }

    @Test void oversizedAndNonJsonAreNotParsed() {
        var observation = new BoundedKafkaObservation();
        String oversized = "x".repeat(BoundedKafkaObservation.MAX_PAYLOAD_BYTES + 1);
        assertFalse(observation.record("oversized", oversized));
        assertTrue(observation.summary().contains("oversized=1"));
        assertTrue(BoundedKafkaObservation.objects(oversized).isEmpty());
        assertTrue(BoundedKafkaObservation.objects("ordinary log " + "x".repeat(10000)).isEmpty());
        assertFalse(BoundedKafkaObservation.withinLimit("я".repeat(600000)));
    }

    @Test void findsNestedAndEncodedServiceSignals() {
        var nodes = BoundedKafkaObservation.objects("{\"message\":\"{\\\"requestId\\\":\\\"wanted\\\",\\\"status\\\":\\\"CONFIG_LOADED\\\"}\"}");
        assertTrue(nodes.stream().anyMatch(n -> n.path("requestId").asText().equals("wanted")));
        assertEquals(2, nodes.size());
        assertEquals(2, BoundedKafkaObservation.objects("{\"message\":{\"status\":\"CONFIG_LOADED\"}}").size());
    }

    @Test void rejectsDeepAndBroadCandidates() {
        assertTrue(BoundedKafkaObservation.objects("[".repeat(100) + "0" + "]".repeat(100)).isEmpty());
        assertTrue(BoundedKafkaObservation.objects("[" + "{},".repeat(17000) + "{}]").isEmpty());
        assertTrue(BoundedKafkaObservation.objects("{\"broken\":}").isEmpty());
    }

    @Test void preservesSignalOrderAcrossNestedObjectsAndEncodedMessage() {
        var nodes = BoundedKafkaObservation.objects("{\"first\":{\"nested\":{\"id\":1}},\"second\":{\"id\":2},\"message\":\"{\\\"id\\\":3}\"}");
        assertEquals(java.util.List.of(1, 2, 3), nodes.stream().filter(n -> n.has("id")).map(n -> n.path("id").asInt()).toList());
    }

    @Test void eachBatchIsConsumedOnceAndSharedClientRemainsOpen() {
        KafkaConsumerClient<?, ?> consumer = mock(KafkaConsumerClient.class);
        var buffered = new ArrayList<Integer>();
        doAnswer(i -> { buffered.clear(); return consumer; }).when(consumer).clear();
        doAnswer(i -> { buffered.add(1); return consumer; }).when(consumer).poll(any(Duration.class));
        int observed = 0;
        for (int i = 0; i < 1000; i++) {
            observed += BoundedKafkaObservation.batch(consumer, Duration.ZERO, buffered::size);
            assertTrue(buffered.isEmpty());
        }
        assertEquals(1000, observed);
        verify(consumer, never()).close();
    }

    @Test void parsingFailureAndPollFailureStillClearTheBuffer() {
        KafkaConsumerClient<?, ?> consumer = mock(KafkaConsumerClient.class);
        assertThrows(IllegalStateException.class, () -> BoundedKafkaObservation.batch(consumer, Duration.ZERO,
                () -> { throw new IllegalStateException("parse failed"); }));
        verify(consumer, times(2)).clear();
        reset(consumer);
        doThrow(new IllegalStateException("poll failed")).when(consumer).poll(any(Duration.class));
        assertThrows(IllegalStateException.class, () -> BoundedKafkaObservation.batch(consumer, Duration.ZERO, () -> null));
        verify(consumer, times(2)).clear();
        verify(consumer, never()).close();
    }
}

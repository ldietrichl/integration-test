package steps.rest.splitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sber.qa.services.kafka.KafkaConsumerClient;
import ru.sber.qa.services.kafka.validation.ValidatableConsumerRecord;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SplitterKafkaReportChecks {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ObjectNode event() {
        var e=JSON.createObjectNode().put("requestId","r");
        e.putObject("messageInfo").put("splittingPoint","REACTIONS");
        e.putObject("message").put("requestId","r").put("splittingConfigVersion",10);
        return e;
    }
    @ParameterizedTest @ValueSource(strings={"request","nestedRequest","point","version","nestedText"})
    void ignoresUnrelatedEnvelopes(String change) {
        var e=event(); assertTrue(SplitterKafkaReportClient.matches(e,"r","REACTIONS",10));
        switch(change) {
            case "request" -> e.put("requestId","foreign");
            case "nestedRequest" -> ((ObjectNode)e.get("message")).put("requestId","foreign");
            case "point" -> ((ObjectNode)e.get("messageInfo")).put("splittingPoint","MAPPER");
            case "version" -> ((ObjectNode)e.get("message")).put("splittingConfigVersion",9);
            case "nestedText" -> e.remove("message");
        }
        e.put("unrelated","r REACTIONS 10");
        assertFalse(SplitterKafkaReportClient.matches(e,"r","REACTIONS",10));
    }
    @Test @SuppressWarnings("unchecked")
    void positionsBeforeRequestClearsBatchesAndKeepsServiceOwnership() {
        KafkaConsumerClient<String,String> consumer=mock(KafkaConsumerClient.class);
        KafkaConsumer<String,String> nativeClient=mock(KafkaConsumer.class);
        when(consumer.getConsumerClient()).thenReturn(nativeClient);
        var partition=new TopicPartition("report",0);
        when(nativeClient.assignment()).thenReturn(Set.of(partition));
        var wrong=event(); ((ObjectNode)wrong.get("message")).put("splittingConfigVersion",9);
        ValidatableConsumerRecord<String,String> old=mock(ValidatableConsumerRecord.class), valid=mock(ValidatableConsumerRecord.class);
        when(old.toConsumerRecord()).thenReturn(new ConsumerRecord<>("report",0,1L,"key",wrong.toString()));
        when(valid.toConsumerRecord()).thenReturn(new ConsumerRecord<>("report",0,2L,"key",event().toString()));
        when(consumer.records()).thenAnswer(call -> Stream.of(old,valid));
        try(var report=new SplitterKafkaReportClient(consumer,"test","report",Duration.ofSeconds(1))) {
            var order=inOrder(consumer,nativeClient);
            order.verify(consumer).assign("report");
            order.verify(consumer).getConsumerClient();
            order.verify(nativeClient).assignment();
            order.verify(nativeClient).seekToEnd(Set.of(partition));
            order.verify(nativeClient).position(partition,Duration.ofSeconds(1));
            verify(consumer,never()).poll(any(Duration.class)); // sending split is safe only after these positions
            assertEquals(event(),report.await("r","REACTIONS",10));
        }
        verify(consumer,atLeast(3)).clear();
        verify(consumer).unsubscribe(); verify(consumer,never()).close();
    }
    @Test @SuppressWarnings("unchecked")
    void missingAssignmentFailsBeforeRequestAndUnsubscribes() {
        KafkaConsumerClient<String,String> consumer=mock(KafkaConsumerClient.class);
        KafkaConsumer<String,String> nativeClient=mock(KafkaConsumer.class);
        when(consumer.getConsumerClient()).thenReturn(nativeClient);
        when(nativeClient.assignment()).thenReturn(Set.of());
        assertThrows(IllegalStateException.class, () -> new SplitterKafkaReportClient(consumer,"test","report",Duration.ofSeconds(1)));
        verify(consumer).unsubscribe();
    }
}

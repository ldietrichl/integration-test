package ru.sber.qa.splitter.extension;

import steps.flow.splitter.workedgroup.WorkedGroupSteps;
import util.splittercheck.WorkedGroupAssertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.services.core.RegressionProfileConfiguration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import util.support.SplitterVersionProvider;
import java.time.Duration;
import java.util.Objects;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(PerfeccionistaExtension.class)
@ResourceLock("splitter-config")
@Execution(ExecutionMode.SAME_THREAD)
abstract class AbstractSplitterDocumentMatrixFlowTest extends WorkedGroupSteps {
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<SplitterDocumentFixtures.Case> documentCases() { return SplitterDocumentFixtures.commonCases(); }

    @CriticalRegression
    @ParameterizedTest(name = "{0}")
    @MethodSource("documentCases")
    void documentMatrix(SplitterDocumentFixtures.Case scenario, KafkaService kafka) {
        runDocumentCase(scenario, kafka);
    }

    protected void runDocumentCase(SplitterDocumentFixtures.Case scenario, KafkaService kafka) {
        var fixture = new SplitterDocumentFixtures().create(scenario, endpointMode() == EndpointMode.REACTIONS,
                SplitterVersionProvider.next());
        String topic = RegressionProfileConfiguration.required("splitter.kap.topic");
        var consumer = kafka.consumerClient(RegressionProfileConfiguration.required("splitter.kap.kafka.env"));
        try {
            consumer.assign(topic);
            var nativeConsumer = consumer.getConsumerClient();
            assertFalse(nativeConsumer.assignment().isEmpty(), "Reporting topic partitions required");
            nativeConsumer.seekToEnd(nativeConsumer.assignment());
            for (var partition : nativeConsumer.assignment()) nativeConsumer.position(partition);
            getFlowWithRest().step("Load document fixture " + scenario,
                            flow -> loadConfig(flow, endpointMode(), fixture.config()))
                    .step("Verify independent REST and Kafka document contracts", flow -> {
                        long sent = System.currentTimeMillis();
                        var response = split(flow, endpointMode(), fixture.request());
                        long received = System.currentTimeMillis();
                        JsonNode api = jsonBody(response, "Document REST response");
                        assertAll("REST and reporting " + scenario,
                                () -> assertAll("REST",
                                        () -> assertBasicResponseContract(response, fixture.request(), fixture.config().getConfigVersion()),
                                        () -> WorkedGroupAssertions.requestTime(api.path("requestDt"), sent, received),
                                        () -> SplitterDocumentOracle.response(api, fixture, false, allowResultWithoutMain())),
                                () -> {
                                    JsonNode payload = null;
                                    long consumed = consumer.records().count();
                                    long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                                    while (payload == null && System.nanoTime() < deadline) {
                                        consumer.poll(Duration.ofMillis(300));
                                        payload = consumer.records().skip(consumed).map(r -> r.toConsumerRecord().value())
                                                .filter(Objects::nonNull).map(AbstractSplitterDocumentMatrixFlowTest::envelope)
                                                .filter(r -> fixture.request().getRequestId().equals(r.path("message").path("requestId").asText(null)))
                                                .findFirst().orElse(null);
                                        consumed = consumer.records().count();
                                    }
                                    assertNotNull(payload, "No correlated Kafka report for " + fixture.request().getRequestId());
                                    io.qameta.allure.Allure.addAttachment("Correlated splitting report", "application/json", payload.toString(), ".json");
                                    JsonNode report = payload.path("message");
                                    String point = payload.path("messageInfo").path("splittingPoint").textValue();
                                    assertAll("Kafka reporting",
                                            () -> SplitterDocumentOracle.correlation(report, api, fixture, point, sent, received),
                                            () -> SplitterDocumentOracle.response(report, fixture, true, allowResultWithoutMain()));
                                });
                    }).run();
        } finally { consumer.unsubscribe(); }
    }

    private static JsonNode envelope(Object value) {
        try {
            JsonNode node = JSON.readTree(value.toString());
            return node == null ? JSON.nullNode() : node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException unrelatedMessage) { return JSON.nullNode(); }
    }
}

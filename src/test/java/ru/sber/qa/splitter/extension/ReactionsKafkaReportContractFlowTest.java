package ru.sber.qa.splitter.extension;

import steps.flow.splitter.workedgroup.WorkedGroupSteps;
import util.splittercheck.WorkedGroupAssertions;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import config.services.core.RegressionProfileConfiguration;
import dto.splitter.config.ExperimentDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import util.support.SplitterVersionProvider;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@Execution(ExecutionMode.SAME_THREAD)
@AnyConfigLoadMode
@Order(30)
@DisplayName("Splitter extension: REACTIONS REST/report exact groups and requestDt")
public class ReactionsKafkaReportContractFlowTest extends WorkedGroupSteps {
    private static final com.fasterxml.jackson.databind.ObjectMapper REPORT_JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    @Override protected EndpointMode endpointMode() { return EndpointMode.REACTIONS; }

    @CriticalRegression
    @ParameterizedTest(name = "worked={0}")
    @CsvSource({"A,0", "B,2500", "C,5000", "NONE,7500"})
    void report(String worked, int from, KafkaService kafka) {
        long version = SplitterVersionProvider.next();
        ExperimentDto experiment = experiment(269090, SALT_2690,
                List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER"),
                        objectParamEqualsCondition(2, "segment", "2690", "INTEGER"),
                        objectParamEqualsCondition(3, "segment", "2690", "INTEGER")),
                List.of(groupWithDocResult("A", shares(0, 2500), 1, "1", "101"),
                        groupWithDocResult("B", shares(2500, 5000), 2, "3", "202"),
                        groupWithEmptyResultParams("C", 5000, 7500, 3)));
        var config = configFor(endpointMode(), version, experiment);
        var request = splitRequest(splittingIdForRange("2690-REPORT", SALT_2690, from, from + 2500),
                object(SINGLE_OBJECT_ID, param("segment", "2690", "INTEGER")));
        boolean main = !worked.equals("NONE") && (endpointMode() == EndpointMode.REACTIONS || !worked.equals("C"));
        String expectedFinal = worked.equals("NONE") ? null : worked;
        String topic = RegressionProfileConfiguration.required("splitter.kap.topic");
        var consumer = kafka.consumerClient(RegressionProfileConfiguration.required("splitter.kap.kafka.env"));
        try {
            // Explicit assignment and resolved end offsets before sending the request avoid a subscription race.
            consumer.assign(topic);
            var nativeConsumer = consumer.getConsumerClient();
            assertFalse(nativeConsumer.assignment().isEmpty(), "Reporting topic partitions required");
            nativeConsumer.seekToEnd(nativeConsumer.assignment());
            for (var partition : nativeConsumer.assignment()) nativeConsumer.position(partition);
            getFlowWithRest().step("Load exact reporting fixture", flow -> loadConfig(flow, endpointMode(), config))
                    .step("Verify REST and Kafka independently", flow -> {
                        long sent = System.currentTimeMillis();
                        var response = split(flow, endpointMode(), request);
                        long received = System.currentTimeMillis();
                        JsonNode api = jsonBody(response, "REST response");
                        assertAll("REST and reporting", () -> assertAll("REST",
                                () -> assertBasicResponseContract(response, request, version),
                                () -> WorkedGroupAssertions.requestTime(api.path("requestDt"), sent, received),
                                () -> {
                                    if (main && endpointMode() == EndpointMode.MAPPER)
                                        WorkedGroupAssertions.filteredFlag(findObjectById(response, SINGLE_OBJECT_ID).path("objectFlags"), false);
                                },
                                () -> {
                                    if (!main) assertObjectWithoutMain(response, SINGLE_OBJECT_ID);
                                    else {
                                        assertRulesExactly(response, SINGLE_OBJECT_ID, "MAIN", "ALL");
                                        for (String rule : List.of("MAIN", "ALL")) {
                                            assertRuleResultSize(response, SINGLE_OBJECT_ID, rule, 1);
                                            verifyRow(firstRuleExp(response, SINGLE_OBJECT_ID, rule), experiment, worked,
                                                    expectedFinal, spread(SALT_2690, request.getSplittingId()));
                                        }
                                    }
                                }), () -> {
                            JsonNode payload = null;
                            long consumed = consumer.records().count();
                            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
                            while (payload == null && System.nanoTime() < deadline) {
                                consumer.poll(Duration.ofMillis(300));
                                payload = consumer.records().skip(consumed).map(r -> r.toConsumerRecord().value()).filter(Objects::nonNull)
                                        .map(this::reportEnvelope)
                                        .filter(r -> request.getRequestId().equals(r.path("message").path("requestId").asText(null)))
                                        .findFirst().orElse(null);
                                consumed = consumer.records().count();
                            }
                            assertNotNull(payload, "No correlated Kafka reporting payload for " + request.getRequestId());
                            io.qameta.allure.Allure.addAttachment("Correlated splitting report", "application/json", payload.toString(), ".json");
                            JsonNode report = payload.path("message");
                            JsonNode envelope = payload;
                            assertAll("Kafka reporting", () -> assertAll("Correlation",
                                    () -> assertEquals(request.getRequestId(), report.path("requestId").textValue()),
                                    () -> assertEquals(request.getSplittingId(), report.path("splittingId").textValue()),
                                    () -> assertEquals(version, report.path("splittingConfigVersion").longValue()),
                                    () -> assertEquals(api.path("responseId"), report.path("responseId")),
                                    () -> assertEquals(endpointMode().splittingPointCode(), envelope.path("messageInfo").path("splittingPoint").textValue())
                                    ), () -> verifyReportBody(report, api, experiment, main, worked,
                                            spread(SALT_2690, request.getSplittingId()), sent, received));
                        });
                    }).run();
        } finally { consumer.unsubscribe(); }
    }

    public void verifyReportBody(JsonNode report, JsonNode api, ExperimentDto experiment, boolean main,
                          String worked, long spread, long sent, long received) {
        String finalGroup = worked.equals("NONE") ? null : worked;
        assertAll("Report body",
                () -> WorkedGroupAssertions.resultTime(report.path("resultDt"), sent, received),
                () -> WorkedGroupAssertions.requestTime(report.path("requestDt"), sent, received),
                () -> WorkedGroupAssertions.correlateRequestTimes(api.path("requestDt"), report.path("requestDt")),
                () -> WorkedGroupAssertions.objects(report, List.of(SINGLE_OBJECT_ID)),
                () -> {
                    JsonNode object = WorkedGroupAssertions.find(report.path("splittingResults"), "objectId", SINGLE_OBJECT_ID);
                    assertAll("Report object",
                            () -> {
                                if (main && endpointMode() == EndpointMode.MAPPER)
                                    WorkedGroupAssertions.filteredFlag(object.path("objectFlags"), false);
                                else if (!main) WorkedGroupAssertions.noMainFlags(object.path("objectFlags"));
                            },
                            () -> WorkedGroupAssertions.rules(object, main ? Set.of("MAIN", "ALL") : Set.of("ALL")),
                            () -> {
                                JsonNode all = WorkedGroupAssertions.find(object.path("objectResults"), "ruleCode", "ALL").path("resultExps");
                                assertAll("ALL",
                                        () -> assertEquals(Set.of("A", "B", "C"), WorkedGroupAssertions.unique(all, "expGroup")),
                                        () -> assertAll("ALL rows", java.util.stream.StreamSupport.stream(all.spliterator(), false)
                                                .map(row -> (org.junit.jupiter.api.function.Executable) () -> verifyRow(row,
                                                        experiment, row.path("expGroup").textValue(), finalGroup, spread))));
                            },
                            () -> {
                                if (!main) return;
                                JsonNode rows = WorkedGroupAssertions.find(object.path("objectResults"), "ruleCode", "MAIN").path("resultExps");
                                assertTrue(rows.isArray());
                                assertEquals(1, rows.size());
                                verifyRow(rows.get(0), experiment, worked, finalGroup, spread);
                            });
                });
    }

    private void verifyRow(JsonNode row, ExperimentDto experiment, String group, String finalGroup, long spread) {
        assertEquals(269090L, row.path("expId").longValue());
        assertEquals(group, row.path("expGroup").textValue());
        assertEquals(group.charAt(0) - 'A' + 1, row.path("conditionId").intValue());
        assertTrue(row.has("finalExpGroup"), "finalExpGroup required");
        assertEquals(finalGroup, row.path("finalExpGroup").textValue());
        WorkedGroupAssertions.experiment(row, experiment, spread);
    }

    private JsonNode reportEnvelope(Object value) {
        try {
            JsonNode parsed = REPORT_JSON.readTree(value.toString());
            return parsed == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : parsed;
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException unrelatedRecord) {
            return com.fasterxml.jackson.databind.node.NullNode.getInstance();
        }
    }
}

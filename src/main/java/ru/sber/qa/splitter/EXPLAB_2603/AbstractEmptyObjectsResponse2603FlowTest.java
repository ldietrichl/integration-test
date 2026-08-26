package ru.sber.qa.splitter.EXPLAB_2603;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;
import flow.RestCustomFlow;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import steps.rest.RestCustomSteps;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import util.splittercheck.SplitterResponseReader;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertTrue;
import static util.TestAssertions.fail;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
abstract class AbstractEmptyObjectsResponse2603FlowTest extends AbstractAnalyticSplitterFlowTest {

    protected static final long EXP_ID = 2603L;
    protected static final String CONDITION_PARAM_CODE = "segmentId";
    protected static final String MATCHING_PARAM_VALUE = "2603";
    protected static final String NOT_MATCHING_PARAM_VALUE = "9999";

    protected static final String MATCHED_OBJECT_ID = "26030000-0000-0000-0000-000000000001";
    protected static final String EMPTY_OBJECT_ID_1 = "26030000-0000-0000-0000-000000000002";
    protected static final String EMPTY_OBJECT_ID_2 = "26030000-0000-0000-0000-000000000003";

    private static final String DEFAULT_KAP_TOPIC = "explab-splitting-result";
    private static final String DEFAULT_MONITORING_TOPIC = "omon_explab_splitter_log";
    private static final Duration DEFAULT_KAFKA_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_MONITORING_TIMEOUT = Duration.ofSeconds(3);

    protected abstract EndpointMode endpointMode();

    protected enum EndpointMode {
        EMPTY_OBJECTS_INCLUDED("MAPPER") {
            @Override
            ValidatableResponseWrapper load(RestCustomSteps steps, LoadConfigRequestDto request) {
                return steps.splitterSteps().loadConfig(request);
            }

            @Override
            ValidatableResponseWrapper split(RestCustomSteps steps, SplitRequestDto request) {
                return steps.splitterSteps().split(request);
            }
        },
        EMPTY_OBJECTS_EXCLUDED("REACTIONS") {
            @Override
            ValidatableResponseWrapper load(RestCustomSteps steps, LoadConfigRequestDto request) {
                return steps.splitterSteps().loadReactionsConfig(request);
            }

            @Override
            ValidatableResponseWrapper split(RestCustomSteps steps, SplitRequestDto request) {
                return steps.splitterSteps().splitReactions(request);
            }
        };

        private final String splittingPointCode;

        EndpointMode(String splittingPointCode) {
            this.splittingPointCode = splittingPointCode;
        }

        String splittingPointCode() {
            return splittingPointCode;
        }

        abstract ValidatableResponseWrapper load(RestCustomSteps steps, LoadConfigRequestDto request);

        abstract ValidatableResponseWrapper split(RestCustomSteps steps, SplitRequestDto request);
    }

    protected LoadConfigRequestDto emptyObjectsConfig(long version) {
        ExperimentDto experiment = experiment((int) EXP_ID,
                "EXPLAB-2603",
                List.of(objectParamEqualsCondition(1, CONDITION_PARAM_CODE, MATCHING_PARAM_VALUE, "INTEGER")),
                List.of(fullRangeGroup("A", 1, "0")));
        LoadConfigRequestDto request = config(version, experiment);
        request.setSplittingPointCode(endpointMode().splittingPointCode());
        return request;
    }

    protected SplitRequestDto mixedSplitRequest(String splittingId) {
        return splitRequest(splittingId, matchedObject(), emptyObject(EMPTY_OBJECT_ID_1), emptyObject(EMPTY_OBJECT_ID_2));
    }

    protected SplitRequestDto onlyEmptyObjectsSplitRequest(String splittingId) {
        return splitRequest(splittingId, emptyObject(EMPTY_OBJECT_ID_1), emptyObject(EMPTY_OBJECT_ID_2));
    }

    protected SplitRequestDto onlyMatchedObjectSplitRequest(String splittingId) {
        return splitRequest(splittingId, matchedObject());
    }

    protected SplitRequestDto singleEmptyObjectSplitRequest(String splittingId) {
        return splitRequest(splittingId, emptyObject(EMPTY_OBJECT_ID_1));
    }

    protected SplittingObjectDto matchedObject() {
        return object(MATCHED_OBJECT_ID, param(CONDITION_PARAM_CODE, MATCHING_PARAM_VALUE, "INTEGER"));
    }

    protected SplittingObjectDto emptyObject(String objectId) {
        return object(objectId, param(CONDITION_PARAM_CODE, NOT_MATCHING_PARAM_VALUE, "INTEGER"));
    }

    protected ValidatableResponseWrapper loadConfig(RestCustomFlow flow, LoadConfigRequestDto config) {
        ValidatableResponseWrapper response = shouldBe200(endpointMode().load(flow.restCustomSteps(), config));
        shouldBeConfigLoaded(response);
        return response;
    }

    protected ValidatableResponseWrapper split(RestCustomFlow flow, SplitRequestDto request) {
        return shouldBe200(endpointMode().split(flow.restCustomSteps(), request));
    }

    protected void assertResponseRequestId(ValidatableResponseWrapper response, SplitRequestDto request) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для проверки requestId");
        assertEquals(request.getRequestId(), root.path("requestId").asText(null), body(response));
    }

    protected void assertObjectPresent(ValidatableResponseWrapper response, String objectId) {
        findObjectById(response, objectId);
    }

    protected void assertObjectHasNonEmptyResults(ValidatableResponseWrapper response, String objectId) {
        JsonNode object = findObjectById(response, objectId);
        JsonNode objectResults = object.path("objectResults");
        assertTrue(objectResults.isArray() && !objectResults.isEmpty(),
                "Ожидали непустой objectResults для objectId=" + objectId + body(response));
    }

    protected void assertObjectHasExpIdAnywhere(ValidatableResponseWrapper response, String objectId, long expId) {
        JsonNode object = findObjectById(response, objectId);
        assertTrue(containsExpId(object, expId),
                "Ожидали expId=" + expId + " в objectResults для objectId=" + objectId + body(response));
    }

    protected void assertObjectWithoutExperimentIsEmpty(ValidatableResponseWrapper response, String objectId) {
        JsonNode object = findObjectById(response, objectId);
        JsonNode objectResults = object.path("objectResults");
        boolean empty = objectResults.isMissingNode()
                || objectResults.isNull()
                || (objectResults.isArray() && objectResults.isEmpty());
        assertTrue(empty,
                "Ожидали пустой objectResults или отсутствие objectResults для объекта без экспериментов: "
                        + objectId + body(response));
        assertFalse(containsExpId(object, EXP_ID),
                "Объект без экспериментов не должен содержать expId=" + EXP_ID + body(response));
    }

    protected void assertResponseObjectIdsExactly(ValidatableResponseWrapper response, String... expectedObjectIds) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для проверки точного состава objectId");
        JsonNode splittingResults = root.path("splittingResults");
        assertTrue(splittingResults.isArray(), "splittingResults должен быть массивом" + body(response));

        List<String> actual = new java.util.ArrayList<>();
        splittingResults.forEach(object -> actual.add(object.path("objectId").asText(null)));
        List<String> expected = Arrays.asList(expectedObjectIds);

        assertEquals(expected.size(), actual.size(),
                "Количество объектов в API-ответе должно быть точным"
                        + "\nexpected=" + expected + "\nactual=" + actual + body(response));
        assertEquals(new LinkedHashSet<>(expected).size(), expected.size(),
                "В ожидаемом наборе objectId не должно быть дублей: " + expected);
        assertEquals(new LinkedHashSet<>(actual).size(), actual.size(),
                "В API-ответе не должно быть дублей objectId: " + actual + body(response));
        assertEquals(new LinkedHashSet<>(expected), new LinkedHashSet<>(actual),
                "API-ответ должен содержать ровно ожидаемый набор objectId"
                        + "\nexpected=" + expected + "\nactual=" + actual + body(response));
    }

    protected void assertKapPayloadContainsRequestAndObjects(KafkaService kafkaService,
                                                             SplitRequestDto request,
                                                             long sinceEpochMillis,
                                                             String... objectIds) {
        String topic = kapTopic();
        String envName = kapKafkaEnv();
        Duration timeout = kapKafkaTimeout();
        Allure.parameter("kap.kafka.env", envName);
        Allure.parameter("kap.kafka.topic", topic);
        Allure.parameter("kap.kafka.timeout", timeout.toString());

        String payload = SplitterKapKafkaAssertions.findKapPayloadByRequestId(
                envName,
                topic,
                request.getRequestId(),
                sinceEpochMillis,
                kafkaService,
                timeout);

        SplitterKapKafkaAssertions.assertKapPayloadHasFullResult(payload, request, objectIds);
    }

    protected void assertNoKapNotSentMonitoringEvent(KafkaService kafkaService,
                                                     SplitRequestDto request,
                                                     long sinceEpochMillis) {
        String topic = monitoringTopic();
        String envName = kapKafkaEnv();
        Duration timeout = monitoringTimeout();
        Allure.parameter("kap.monitoring.kafka.env", envName);
        Allure.parameter("kap.monitoring.topic", topic);
        Allure.parameter("kap.monitoring.timeout", timeout.toString());

        SplitterKapKafkaAssertions.assertNoNotSentMonitoringEvent(
                envName,
                topic,
                request.getRequestId(),
                sinceEpochMillis,
                kafkaService,
                timeout);
    }

    private String kapKafkaEnv() {
        return System.getProperty("splitter.kap.kafka.env", TEST_CONFIG.env());
    }

    private String kapTopic() {
        return System.getProperty("splitter.kap.topic", DEFAULT_KAP_TOPIC);
    }

    private String monitoringTopic() {
        return System.getProperty("splitter.kap.monitoring.topic", DEFAULT_MONITORING_TOPIC);
    }

    private Duration kapKafkaTimeout() {
        long seconds = Long.parseLong(System.getProperty("splitter.kap.timeout.seconds", String.valueOf(DEFAULT_KAFKA_TIMEOUT.toSeconds())));
        return Duration.ofSeconds(seconds);
    }

    private Duration monitoringTimeout() {
        long seconds = Long.parseLong(System.getProperty("splitter.kap.monitoring.timeout.seconds", String.valueOf(DEFAULT_MONITORING_TIMEOUT.toSeconds())));
        return Duration.ofSeconds(seconds);
    }

    private JsonNode findObjectById(ValidatableResponseWrapper response, String objectId) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для поиска objectId=" + objectId);
        JsonNode splittingResults = root.path("splittingResults");
        assertTrue(splittingResults.isArray(), "splittingResults должен быть массивом" + body(response));
        for (JsonNode object : splittingResults) {
            if (Objects.equals(objectId, object.path("objectId").asText(null))) {
                return object;
            }
        }
        fail("Не найден objectId=" + objectId + body(response));
        return null;
    }

    private JsonNode jsonBody(ValidatableResponseWrapper response, String assertionContext) {
        return SplitterResponseReader.snapshot(response).requireJsonBody(assertionContext);
    }

    private boolean containsExpId(JsonNode object, long expId) {
        JsonNode objectResults = object.path("objectResults");
        if (!objectResults.isArray()) {
            return false;
        }
        for (JsonNode ruleResult : objectResults) {
            JsonNode resultExps = ruleResult.path("resultExps");
            if (!resultExps.isArray()) {
                continue;
            }
            for (JsonNode resultExp : resultExps) {
                if (resultExp.path("expId").asLong(Long.MIN_VALUE) == expId) {
                    return true;
                }
            }
        }
        return false;
    }
}



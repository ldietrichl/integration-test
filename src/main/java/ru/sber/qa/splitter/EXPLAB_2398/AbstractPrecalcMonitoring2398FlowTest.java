package ru.sber.qa.splitter.EXPLAB_2398;

import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;
import io.qameta.allure.Allure;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import util.support.SplitterVersionProvider;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

abstract class AbstractPrecalcMonitoring2398FlowTest extends AbstractAnalyticSplitterFlowTest {

    protected static final int EXP_ID = 239801;
    protected static final String CONDITION_PARAM_CODE = "segment2398";
    protected static final String MATCHING_VALUE = "YES";
    protected static final String NOT_MATCHING_VALUE = "NO";
    protected static final String SPLIT_OBJECT_ID_1 = "23980000-0000-0000-0000-000000000001";
    protected static final String SPLIT_OBJECT_ID_2 = "23980000-0000-0000-0000-000000000002";
    protected static final String SPLIT_OBJECT_ID_3 = "23980000-0000-0000-0000-000000000003";

    private static final String DEFAULT_MONITORING_TOPIC = "omon_explab_splitter_log";
    private static final Duration DEFAULT_MONITORING_TIMEOUT = Duration.ofSeconds(30);
    private static final AtomicInteger SO_VERSION = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));

    protected LoadConfigRequestDto oneExperimentConfig() {
        long version = SplitterVersionProvider.next();
        ExperimentDto experiment = experiment(
                EXP_ID,
                "EXPLAB-2398-SALT",
                List.of(objectParamEqualsCondition(1, CONDITION_PARAM_CODE, MATCHING_VALUE, "STRING")),
                List.of(fullRangeGroup("A", 1, "0")));
        return config(version, experiment);
    }

    protected int nextSoConfigVersion() {
        return SO_VERSION.incrementAndGet();
    }

    protected String uc(String scenario, int index) {
        return "explab-2398-" + scenario + "-" + System.nanoTime() + "-uc-" + index;
    }

    protected SplitterPrecalcRequestDto precalcRequest(int soConfigVersion, SplitterPrecalcObjectDto... objects) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(soConfigVersion)
                .splittingObjects(Arrays.asList(objects))
                .build();
    }

    protected SplitterPrecalcObjectDto matchingPrecalcObject(String uniqueConfigurationId) {
        return precalcObject(uniqueConfigurationId, MATCHING_VALUE);
    }

    protected SplitterPrecalcObjectDto nonMatchingPrecalcObject(String uniqueConfigurationId) {
        return precalcObject(uniqueConfigurationId, NOT_MATCHING_VALUE);
    }

    protected SplitRequestDto splitByPrecalcOnlyRequest(String splittingId, SplittingObjectDto... objects) {
        return splitRequest(splittingId, objects);
    }

    protected SplittingObjectDto splitObjectByPrecalcOnly(String uniqueConfigurationId, String objectId) {
        return objectWithUniqueId(uniqueConfigurationId, objectId,
                param("runtimeOnly2398", "RUNTIME_PARAM_DOES_NOT_MATCH_CONFIG", "STRING"));
    }

    protected ValidatableResponseWrapper loadConfig(FlowWithRest flow, LoadConfigRequestDto config) {
        ValidatableResponseWrapper response = shouldBe200(flow.restCustomSteps().splitterSteps().loadConfig(config));
        shouldBeConfigLoaded(response);
        return response;
    }

    protected ValidatableResponseWrapper calculatePreliminary(FlowWithRest flow, SplitterPrecalcRequestDto request) {
        ValidatableResponseWrapper response = shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(request));
        shouldHaveSoConfigVersion(response, request.getSoConfigVersion());
        return response;
    }

    protected ValidatableResponseWrapper split(FlowWithRest flow, SplitRequestDto request) {
        return shouldBe200(flow.restCustomSteps().splitterSteps().split(request));
    }

    protected void assertMonitoringEvent(KafkaService kafkaService,
                                         long sinceEpochMillis,
                                         PrecalcMonitoring2398EventExpectation expectation) {
        Allure.parameter("precalc.monitoring.kafka.env", monitoringKafkaEnv());
        Allure.parameter("precalc.monitoring.topic", monitoringTopic());
        Allure.parameter("precalc.monitoring.timeout", monitoringTimeout().toString());

        PrecalcMonitoring2398KafkaAssertions.assertPrecalcMonitoringEvent(
                kafkaService,
                monitoringKafkaEnv(),
                monitoringTopic(),
                sinceEpochMillis,
                monitoringTimeout(),
                expectation);
    }

    protected PrecalcMonitoring2398EventExpectation loadedFirstExpectation(SplitterPrecalcRequestDto request,
                                                                           long objectsAdded,
                                                                           long objectsDeleted,
                                                                           long notLinkedObjects,
                                                                           long totalObjects,
                                                                           long linkedExps,
                                                                           long totalExps) {
        return PrecalcMonitoring2398EventExpectation.event(request.getRequestId(), "LOADED_FIRST", request.getSoConfigVersion())
                .counter("objectsAdded", objectsAdded)
                .counter("objectsDeleted", objectsDeleted)
                .counter("notLinkedObjects", notLinkedObjects)
                .counter("totalObjects", totalObjects)
                .counter("linkedExps", linkedExps)
                .counter("totalExps", totalExps);
    }

    protected PrecalcMonitoring2398EventExpectation validationFailedExpectation(SplitterPrecalcRequestDto request) {
        return PrecalcMonitoring2398EventExpectation.event(request.getRequestId(), "VALIDATION_FAILED", request.getSoConfigVersion());
    }

    protected PrecalcMonitoring2398EventExpectation precalcNotEnabledExpectation(SplitterPrecalcRequestDto request) {
        return PrecalcMonitoring2398EventExpectation.event(request.getRequestId(), "REQUEST_REJECTED_PRECALC_NOT_ENABLED", request.getSoConfigVersion());
    }

    private SplitterPrecalcObjectDto precalcObject(String uniqueConfigurationId, String value) {
        return SplitterPrecalcObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectParams(List.of(new SplitterPrecalcParamDto(CONDITION_PARAM_CODE, List.of(value), "STRING")))
                .build();
    }

    private String monitoringKafkaEnv() {
        return System.getProperty("splitter.precalc.monitoring.kafka.env", TEST_CONFIG.env());
    }

    private String monitoringTopic() {
        return System.getProperty("splitter.precalc.monitoring.topic", DEFAULT_MONITORING_TOPIC);
    }

    private Duration monitoringTimeout() {
        long seconds = Long.parseLong(System.getProperty(
                "splitter.precalc.monitoring.timeout.seconds",
                String.valueOf(DEFAULT_MONITORING_TIMEOUT.toSeconds())));
        return Duration.ofSeconds(seconds);
    }
}

package ru.sber.qa.splitter.EXPLAB_2739;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
import dto.splitter.config.SplittingConfigDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.support.KafkaConfigLoadModeOnly;
import ru.sber.qa.splitter.support.KafkaConfigStatusRequiredOnly;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@KafkaConfigLoadModeOnly
@DisplayName("EXPLAB-2739. Kafka config load: remaining functional plan")
public class SplitterConfigKafkaRemaining2739FlowTest extends AbstractSplitterV9FlowTest {

    private static final AtomicInteger SO_VERSION = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
    private static final String SALT = "EXPLAB-2739-REMAINING-SALT";
    private static final String OBJECT = "explab-2739-object";
    private final SplitterConfigKafkaLoad2739Flow kafkaFlow = new SplitterConfigKafkaLoad2739Flow();

    @Test
    @DisplayName("EXPLAB-2739-CFG-01. Kafka-загрузка валидного config публикует CONFIG_LOADED и активирует config")
    void kafkaValidConfigShouldPublishLoadedStatusAndBecomeActive(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, true, markerExperiment(27390101, "CFG01", "0"));
        SplitRequestDto splitRequest = splitRequest("EXPLAB-2739-CFG-01",
                objectWithUniqueId("explab-2739-cfg-01-uid", OBJECT, param("marker", "CFG01", "STRING")));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Отправляем валидный config в Kafka", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(config);
                })
                .step("Ждем Kafka load signal CONFIG_LOADED", flow ->
                        waitForLoadedSignal(kafkaService, config, since[0]))
                .step("Проверяем, что config активен для split", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, version);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 27390101L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-02. Старая версия без forceConfigLoad отклоняется через Kafka")
    void kafkaOldVersionWithoutForceShouldBeRejected(KafkaService kafkaService) {
        long currentVersion = SplitterVersionProvider.next();
        long oldVersion = SplitterVersionProvider.nextOlderThan(currentVersion);
        LoadConfigRequestDto currentConfig = config(currentVersion, true, markerExperiment(27390201, "CFG02", "0"));
        LoadConfigRequestDto oldConfig = config(oldVersion, false, markerExperiment(27390202, "CFG02", "6"));
        SplitRequestDto splitRequest = splitRequest("EXPLAB-2739-CFG-02",
                objectWithUniqueId("explab-2739-cfg-02-uid", OBJECT, param("marker", "CFG02", "STRING")));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Загружаем текущую версию через Kafka", flow -> {
                    long seedSince = System.currentTimeMillis();
                    kafkaFlow.sendConfig(currentConfig);
                    waitForLoadedSignal(kafkaService, currentConfig, seedSince);
                })
                .step("Отправляем старую версию с forceConfigLoad=false", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(oldConfig);
                })
                .step("Проверяем CONFIG_NOT_LOADED и NOT_LOADED_OLD_VERSION", flow -> {
                    assertStatusIfRequired(kafkaService, oldConfig, since[0], "CONFIG_NOT_LOADED");
                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            oldConfig.getMessageId(),
                            "NOT_LOADED_OLD_VERSION",
                            since[0]);
                    assertMonitoringCommon(monitoring, oldConfig, "NOT_LOADED_OLD_VERSION");
                })
                .step("Проверяем, что active config не изменился", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, currentVersion);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 27390201L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-03. forceConfigLoad=true загружает старую версию через Kafka")
    void kafkaForcedOldVersionShouldLoad(KafkaService kafkaService) {
        long currentVersion = SplitterVersionProvider.next();
        long forcedOldVersion = SplitterVersionProvider.nextOlderThan(currentVersion);
        LoadConfigRequestDto currentConfig = config(currentVersion, true, markerExperiment(27390301, "CFG03", "0"));
        LoadConfigRequestDto forcedOldConfig = config(forcedOldVersion, true, markerExperiment(27390302, "CFG03", "6"));
        SplitRequestDto splitRequest = splitRequest("EXPLAB-2739-CFG-03",
                objectWithUniqueId("explab-2739-cfg-03-uid", OBJECT, param("marker", "CFG03", "STRING")));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Загружаем текущую версию через Kafka", flow -> {
                    long seedSince = System.currentTimeMillis();
                    kafkaFlow.sendConfig(currentConfig);
                    waitForLoadedSignal(kafkaService, currentConfig, seedSince);
                })
                .step("Отправляем старую версию с forceConfigLoad=true", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(forcedOldConfig);
                })
                .step("Ждем CONFIG_LOADED для forced old version", flow ->
                        waitForLoadedSignal(kafkaService, forcedOldConfig, since[0]))
                .step("Проверяем, что active config стал forced-old", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, forcedOldVersion);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 27390302L, "A", "A");
                    assertFirstRuleExpActionType(response, OBJECT, "MAIN", "6");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-04. Config с чужим splittingPointCode не меняет MAPPER active config")
    void kafkaForeignSplittingPointShouldNotOverrideMapperConfig(KafkaService kafkaService) {
        long currentVersion = SplitterVersionProvider.next();
        long foreignVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto currentConfig = config(currentVersion, true, markerExperiment(27390401, "CFG04", "0"));
        LoadConfigRequestDto foreignConfig = config(foreignVersion, true, markerExperiment(27390402, "CFG04", "6"));
        foreignConfig.setSplittingPointCode("REACTIONS");
        SplitRequestDto splitRequest = splitRequest("EXPLAB-2739-CFG-04",
                objectWithUniqueId("explab-2739-cfg-04-uid", OBJECT, param("marker", "CFG04", "STRING")));

        getFlowWithRest()
                .step("Загружаем текущую MAPPER версию через Kafka", flow -> {
                    long seedSince = System.currentTimeMillis();
                    kafkaFlow.sendConfig(currentConfig);
                    waitForLoadedSignal(kafkaService, currentConfig, seedSince);
                })
                .step("Отправляем Kafka config для чужой точки REACTIONS", flow -> kafkaFlow.sendConfig(foreignConfig))
                .step("Проверяем, что MAPPER split остался на исходной версии", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, currentVersion);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 27390401L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-05. REQUEST_PARAMS при pre-calculate enabled отклоняется через Kafka")
    void kafkaRequestParamsConfigShouldBeRejected(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, true, requestParamsExperiment(27390501));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Отправляем config с REQUEST_PARAMS", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(config);
                })
                .step("Проверяем CONFIG_NOT_LOADED и monitoring REQUEST_PARAMS_WITH_PRECALC_ENABLED", flow -> {
                    assertStatusIfRequired(kafkaService, config, since[0], "CONFIG_NOT_LOADED");
                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            config.getMessageId(),
                            "REQUEST_PARAMS_WITH_PRECALC_ENABLED",
                            since[0]);
                    assertMonitoringCommon(monitoring, config, "REQUEST_PARAMS_WITH_PRECALC_ENABLED");
                })
                .run();
    }

    @Test
    @KafkaConfigStatusRequiredOnly
    @DisplayName("EXPLAB-2739-CFG-06. Structurally invalid Kafka payload пишет VALIDATION_FAILED")
    void kafkaInvalidPayloadShouldWriteValidationFailedMonitoring(KafkaService kafkaService) {
        String messageId = UUID.randomUUID().toString();
        String payload = "{ \"messageId\": \"" + messageId + "\", \"requestId\": \"" + UUID.randomUUID()
                + "\", \"configVersion\": " + SplitterVersionProvider.next()
                + ", \"forceConfigLoad\": true, \"splittingPointCode\": \"MAPPER\", \"splittingConfig\": { \"experiments\": \"not-array\" } }";
        long[] since = new long[1];

        getFlowWithRest()
                .step("Отправляем structurally invalid payload", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendRaw(messageId, payload);
                })
                .step("Проверяем monitoring VALIDATION_FAILED", flow -> {
                    assumeTrue(kafkaFlow.isStatusRequired(),
                            "Локальный SDK-host не публикует monitoring для ошибок Spring Kafka deserialization");
                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            messageId,
                            "VALIDATION_FAILED",
                            since[0]);
                    assertEquals("SPLITTING_CONFIG_LOAD", SplitterConfigKafkaLoad2739Flow.normalizedText(monitoring, "function"),
                            monitoring.toPrettyString());
                    assertEquals("VALIDATION_FAILED", SplitterConfigKafkaLoad2739Flow.normalizedText(monitoring, "result"),
                            monitoring.toPrettyString());
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-07. Config без salt отклоняется через Kafka")
    void kafkaConfigWithoutSaltShouldBeRejected(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                true,
                ExperimentDto.builder()
                        .id(27390701)
                        .purpose("DCG")
                        .salt(null)
                        .objectSelectConditions(List.of(markerCondition(1, "CFG07")))
                        .groups(List.of(actionGroup("A", 1, "0")))
                        .build());
        long[] since = new long[1];

        getFlowWithRest()
                .step("Отправляем config без salt", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(config);
                })
                .step("Проверяем CONFIG_NOT_LOADED и monitoring VALIDATION_FAILED", flow -> {
                    assertStatusIfRequired(kafkaService, config, since[0], "CONFIG_NOT_LOADED");
                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            config.getMessageId(),
                            "VALIDATION_FAILED",
                            since[0]);
                    assertMonitoringCommon(monitoring, config, "VALIDATION_FAILED");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-09. Kafka reload с существующей predcalc-table пишет LOADED_WITH_PRECALC")
    void kafkaReloadWithPrecalcTableShouldWriteLoadedWithPrecalc(KafkaService kafkaService) {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        String uid = "explab-2739-cfg-09-" + System.nanoTime();
        LoadConfigRequestDto seed = config(versions[0], true, markerExperiment(27390901, "CFG09", "0"));
        LoadConfigRequestDto reload = config(versions[1], true, markerExperiment(27390902, "CFG09", "3"));
        SplitterPrecalcRequestDto precalc = precalcRequest(uid, "CFG09");
        long[] since = new long[1];

        getFlowWithRest()
                .step("Загружаем seed config через Kafka", flow -> {
                    long seedSince = System.currentTimeMillis();
                    kafkaFlow.sendConfig(seed);
                    waitForLoadedSignal(kafkaService, seed, seedSince);
                })
                .step("Создаем predcalc table", flow ->
                        shouldHaveSoConfigVersion(shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(precalc)),
                                precalc.getSoConfigVersion()))
                .step("Отправляем reload config через Kafka", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(reload);
                })
                .step("Проверяем status CONFIG_LOADED и monitoring LOADED_WITH_PRECALC", flow -> {
                    assertStatusIfRequired(kafkaService, reload, since[0], "CONFIG_LOADED");
                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            reload.getMessageId(),
                            "LOADED_WITH_PRECALC",
                            since[0]);
                    assertMonitoringCommon(monitoring, reload, "LOADED_WITH_PRECALC");
                    assertNumericField(monitoring, "notLinkedExps");
                    assertNumericField(monitoring, "totalExps");
                    assertNumericField(monitoring, "notLinkedObjects");
                })
                .run();
    }

    @Disabled("Требуется отдельный стендовый профиль с preliminary-calc-enabled=false")
    @Test
    @DisplayName("EXPLAB-2739-CFG-10. Manual/env: Kafka load при выключенном pre-calculate")
    void kafkaLoadWithPrecalcDisabledShouldLoadAndPrecalculateShouldReject() {
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-11. Config с некорректными shares отклоняется через Kafka")
    void kafkaInvalidSharesShouldBeRejected(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                true,
                experiment(27391101,
                        SALT,
                        List.of(markerCondition(1, "CFG11")),
                        List.of(group("A",
                                Arrays.asList(share(0, 7000), share(6000, 10000)),
                                List.of(resultWithParams(1, param("actionType", "0", "INTEGER")))))));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Отправляем config с пересекающимися shares", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(config);
                })
                .step("Проверяем rejection signal", flow -> {
                    if (kafkaFlow.isStatusRequired()) {
                        assertStatus(kafkaFlow.findStatusByConfigMessageId(kafkaService, config.getMessageId(), since[0]),
                                config,
                                "CONFIG_NOT_LOADED");
                    } else {
                        JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                                config.getMessageId(),
                                "VALIDATION_FAILED",
                                since[0]);
                        assertMonitoringCommon(monitoring, config, "VALIDATION_FAILED");
                    }
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-12. Kafka config с experiments=[] загружается и split пустой")
    void kafkaEmptyExperimentsConfigShouldLoadAndReturnEmptySplit(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(List.of())
                        .build())
                .build();
        SplitRequestDto splitRequest = splitRequest("EXPLAB-2739-CFG-12",
                objectWithUniqueId("explab-2739-cfg-12-uid", OBJECT, param("marker", "EMPTY", "STRING")));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Отправляем config с experiments=[] через Kafka", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(config);
                })
                .step("Ждем Kafka load signal CONFIG_LOADED", flow ->
                        waitForLoadedSignal(kafkaService, config, since[0]))
                .step("Проверяем, что split не падает и не возвращает experiments", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, version);
                    assertObjectEmptyOrAbsent(response, OBJECT);
                })
                .run();
    }

    @Test
    @KafkaConfigStatusRequiredOnly
    @DisplayName("EXPLAB-2739-CFG-13. Status после successful load содержит контрактные поля")
    void kafkaLoadedStatusShouldContainContractFields(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, true, markerExperiment(27391301, "CFG13", "0"));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Отправляем валидный config", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(config);
                })
                .step("Проверяем status contract", flow -> {
                    assumeTrue(kafkaFlow.isStatusRequired(),
                            "Локальный SDK-host не публикует ConfigResultMessage/STATUS при Kafka-load");
                    JsonNode status = kafkaFlow.findStatusByConfigMessageId(kafkaService, config.getMessageId(), since[0]);
                    assertStatus(status, config, "CONFIG_LOADED");
                    assertNotNull(SplitterConfigKafkaLoad2739Flow.text(status, "messageId"), status.toPrettyString());
                    assertEquals(config.getMessageId(), SplitterConfigKafkaLoad2739Flow.text(status, "configMessageId"),
                            status.toPrettyString());
                    assertEquals(String.valueOf(config.getConfigVersion()),
                            SplitterConfigKafkaLoad2739Flow.text(status, "newConfigVersion"),
                            status.toPrettyString());
                    assertEquals("MAPPER", SplitterConfigKafkaLoad2739Flow.text(status, "splittingPointCode"),
                            status.toPrettyString());
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2739-CFG-14. Error monitoring содержит базовый contract")
    void kafkaErrorMonitoringShouldContainCommonContract(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, true, requestParamsExperiment(27391401));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Отправляем config, который должен быть rejected", flow -> {
                    since[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(config);
                })
                .step("Проверяем common monitoring contract", flow -> {
                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            config.getMessageId(),
                            "REQUEST_PARAMS_WITH_PRECALC_ENABLED",
                            since[0]);
                    assertMonitoringCommon(monitoring, config, "REQUEST_PARAMS_WITH_PRECALC_ENABLED");
                    assertNotNull(SplitterConfigKafkaLoad2739Flow.textAny(monitoring, "completedTimestamp", "kafkaDtIn"),
                            monitoring.toPrettyString());
                    assertNotNull(SplitterConfigKafkaLoad2739Flow.textAny(monitoring, "splittingPointCode", "splittingPoing", "splittingPoint"),
                            monitoring.toPrettyString());
                })
                .run();
    }

    @Disabled("Требуется отдельный стендовый профиль с splitter.config.api-config-load=true")
    @Test
    @DisplayName("EXPLAB-2739-CFG-15. Manual/env: api-config-load=true отключает Kafka consumer")
    void apiConfigLoadTrueShouldDisableKafkaConsumerProcessing() {
    }

    private LoadConfigRequestDto config(long version, boolean forceConfigLoad, ExperimentDto experiment) {
        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(forceConfigLoad)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(List.of(experiment))
                        .build())
                .build();
    }

    private ExperimentDto markerExperiment(int id, String marker, String actionType) {
        return experiment(id,
                SALT + "-" + id,
                List.of(markerCondition(1, marker)),
                List.of(actionGroup("A", 1, actionType)));
    }

    private ExperimentDto requestParamsExperiment(int id) {
        RuleDto rule = RuleDto.builder()
                .dataType("STRING")
                .paramCode("requestMarker")
                .paramSource("REQUEST_PARAMS")
                .operatorCode("equal")
                .values(List.of("REQ"))
                .build();
        return experiment(id,
                SALT + "-" + id,
                List.of(condition(1, List.of(List.of(rule)))),
                List.of(actionGroup("A", 1, "0")));
    }

    private ObjectSelectConditionDto markerCondition(int id, String marker) {
        return condition(id, List.of(List.of(RuleDto.builder()
                .dataType("STRING")
                .paramCode("marker")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("equal")
                .values(List.of(marker))
                .build())));
    }

    private GroupDto actionGroup(String code, int conditionId, String actionType) {
        return group(code,
                Arrays.asList(share(0, 10000)),
                List.of(resultWithParams(conditionId, param("actionType", actionType, "INTEGER"))));
    }

    private SplitterPrecalcRequestDto precalcRequest(String uid, String marker) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(SO_VERSION.incrementAndGet())
                .splittingObjects(List.of(SplitterPrecalcObjectDto.builder()
                        .uniqueConfigurationId(uid)
                        .objectParams(List.of(new SplitterPrecalcParamDto("marker", List.of(marker), "STRING")))
                        .build()))
                .build();
    }

    private void assertStatus(JsonNode status, LoadConfigRequestDto config, String expectedStatus) {
        assertEquals("STATUS", SplitterConfigKafkaLoad2739Flow.normalizedText(status, "messageType"),
                status.toPrettyString());
        assertEquals(expectedStatus, SplitterConfigKafkaLoad2739Flow.normalizedText(status, "status"),
                status.toPrettyString());
        assertEquals(config.getMessageId(), SplitterConfigKafkaLoad2739Flow.text(status, "configMessageId"),
                status.toPrettyString());
        assertEquals(String.valueOf(config.getConfigVersion()),
                SplitterConfigKafkaLoad2739Flow.textAny(status, "newConfigVersion", "currentConfigVersion"),
                status.toPrettyString());
    }

    private void waitForLoadedSignal(KafkaService kafkaService, LoadConfigRequestDto config, long since) {
        if (kafkaFlow.isStatusRequired()) {
            assertStatusIfRequired(kafkaService, config, since, "CONFIG_LOADED");
        } else {
            JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                    config.getMessageId(),
                    "LOADED_WITH_PRECALC",
                    since);
            assertMonitoringCommon(monitoring, config, "LOADED_WITH_PRECALC");
        }
    }

    private void assertStatusIfRequired(KafkaService kafkaService,
                                        LoadConfigRequestDto config,
                                        long since,
                                        String expectedStatus) {
        if (!kafkaFlow.isStatusRequired()) {
            return;
        }
        assertStatus(kafkaFlow.findStatusByConfigMessageId(kafkaService, config.getMessageId(), since),
                config,
                expectedStatus);
    }

    private void assertMonitoringCommon(JsonNode monitoring, LoadConfigRequestDto config, String expectedResult) {
        assertEquals("SPLITTING_CONFIG_LOAD", SplitterConfigKafkaLoad2739Flow.normalizedText(monitoring, "function"),
                monitoring.toPrettyString());
        assertEquals(expectedResult, SplitterConfigKafkaLoad2739Flow.normalizedText(monitoring, "result"),
                monitoring.toPrettyString());
        assertEquals("KAFKA", SplitterConfigKafkaLoad2739Flow.text(monitoring, "loadMethod"),
                monitoring.toPrettyString());
        assertEquals(config.getMessageId(),
                SplitterConfigKafkaLoad2739Flow.textAny(monitoring, "messageId", "requestIdIn", "configMessageId"),
                monitoring.toPrettyString());
        assertEquals("splitter-service", SplitterConfigKafkaLoad2739Flow.text(monitoring, "service"),
                monitoring.toPrettyString());
    }

    private void assertNumericField(JsonNode node, String field) {
        assertTrue(node.path(field).canConvertToLong(), "Ожидали numeric field " + field + "\n" + node.toPrettyString());
    }

}

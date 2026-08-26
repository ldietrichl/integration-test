package ru.sber.qa.splitter.EXPLAB_2399;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import util.support.SplitterVersionProvider;

import java.util.UUID;

import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigKafkaLoad2399TestData.SO_CONFIG_VERSION;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigKafkaLoad2399TestData.invalidNoTrafficRulesConfig;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigKafkaLoad2399TestData.invalidStructureMessage;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigKafkaLoad2399TestData.oldVersionConfig;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigKafkaLoad2399TestData.precalcRequest;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigKafkaLoad2399TestData.requestParamsConfig;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigKafkaLoad2399TestData.validObjectParamConfig;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigLoadMonitoring2399Assertions.assertInvalidMessageMonitoring;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigLoadMonitoring2399Assertions.assertLoadedWithPrecalcMonitoring;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigLoadMonitoring2399Assertions.assertOldVersionMonitoring;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigLoadMonitoring2399Assertions.assertRequestParamsWithPrecalcMonitoring;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigLoadMonitoring2399Assertions.assertStatus;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigLoadMonitoring2399Assertions.assertStatusDescContains;
import static ru.sber.qa.splitter.EXPLAB_2399.SplitterConfigLoadMonitoring2399Assertions.assertValidationFailedMonitoring;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterConfigLoadKafkaMonitoring2399FlowTest extends AbstractAnalyticSplitterFlowTest {

    private static final int EXP_ID_SEED = 239900;
    private static final int EXP_ID_RELOAD = 239901;
    private static final int EXP_ID_OLD_VERSION = 239902;
    private static final int EXP_ID_REQUEST_PARAMS = 239903;
    private static final int EXP_ID_INVALID_CONFIG = 239904;

    private final SplitterConfigKafkaLoad2399Flow kafkaFlow = new SplitterConfigKafkaLoad2399Flow();

    @Test
    @DisplayName("EXPLAB-2399-01. Kafka load: при наличии таблицы предрасчета пишется monitoring LOADED_WITH_PRECALC")
    void kafkaConfigLoadShouldWriteLoadedWithPrecalcMonitoring(KafkaService kafkaService) {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        String seedMarker = "seed-" + versions[0];
        String reloadMarker = "reload-" + versions[1];
        String uniqueConfigurationId = "explab-2399-uc-" + versions[0];

        LoadConfigRequestDto seedConfig = validObjectParamConfig(versions[0], EXP_ID_SEED, seedMarker);
        SplitterPrecalcRequestDto precalcRequest = precalcRequest(uniqueConfigurationId, seedMarker);
        LoadConfigRequestDto kafkaConfig = validObjectParamConfig(versions[1], EXP_ID_RELOAD, reloadMarker);
        long[] kafkaSince = new long[1];

        getFlowWithRest()
                .step("Загружаем seed-конфигурацию только через Kafka и ждем CONFIG_LOADED", flow -> {
                    long seedSince = System.currentTimeMillis();
                    kafkaFlow.sendConfig(seedConfig);
                    JsonNode seedStatus = kafkaFlow.findStatusByConfigMessageId(kafkaService,
                            seedConfig.getMessageId(),
                            seedSince);
                    assertStatus(seedStatus, seedConfig, "CONFIG_LOADED");
                })
                .step("Создаем таблицу предрасчета через pre-calculate", flow -> {
                    var precalcResponse = shouldBe200(
                            flow.restCustomSteps().splitterSteps().calculatePreliminary(precalcRequest));
                    shouldHaveSoConfigVersion(precalcResponse, SO_CONFIG_VERSION);
                })
                .step("Отправляем новую конфигурацию в Kafka topic splitting-config-created", flow -> {
                    kafkaSince[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(kafkaConfig);
                })
                .step("Проверяем статус CONFIG_LOADED и monitoring LOADED_WITH_PRECALC с метриками предрасчета", flow -> {
                    JsonNode status = kafkaFlow.findStatusByConfigMessageId(kafkaService,
                            kafkaConfig.getMessageId(),
                            kafkaSince[0]);
                    assertStatus(status, kafkaConfig, "CONFIG_LOADED");

                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            kafkaConfig.getMessageId(),
                            "LOADED_WITH_PRECALC",
                            kafkaSince[0]);
                    assertLoadedWithPrecalcMonitoring(monitoring, kafkaConfig);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2399-02. Kafka load: старая версия отклоняется и пишется monitoring NOT_LOADED_OLD_VERSION")
    void kafkaOldConfigVersionShouldWriteNotLoadedOldVersionMonitoring(KafkaService kafkaService) {
        long currentVersion = SplitterVersionProvider.nextVersion();
        long oldVersion = SplitterVersionProvider.nextOlderThan(currentVersion);
        LoadConfigRequestDto currentConfig = validObjectParamConfig(currentVersion, EXP_ID_SEED, "current-" + currentVersion);
        LoadConfigRequestDto oldKafkaConfig = oldVersionConfig(oldVersion, EXP_ID_OLD_VERSION, "old-" + oldVersion);
        long[] kafkaSince = new long[1];

        getFlowWithRest()
                .step("Загружаем текущую более новую конфигурацию только через Kafka и ждем CONFIG_LOADED", flow -> {
                    long currentSince = System.currentTimeMillis();
                    kafkaFlow.sendConfig(currentConfig);
                    JsonNode currentStatus = kafkaFlow.findStatusByConfigMessageId(kafkaService,
                            currentConfig.getMessageId(),
                            currentSince);
                    assertStatus(currentStatus, currentConfig, "CONFIG_LOADED");
                })
                .step("Отправляем через Kafka конфигурацию с версией младше текущей и forceConfigLoad=false", flow -> {
                    kafkaSince[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(oldKafkaConfig);
                })
                .step("Проверяем статус CONFIG_NOT_LOADED и monitoring NOT_LOADED_OLD_VERSION", flow -> {
                    JsonNode status = kafkaFlow.findStatusByConfigMessageId(kafkaService,
                            oldKafkaConfig.getMessageId(),
                            kafkaSince[0]);
                    assertStatus(status, oldKafkaConfig, "CONFIG_NOT_LOADED");
                    assertStatusDescContains(status, "Версия");

                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            oldKafkaConfig.getMessageId(),
                            "NOT_LOADED_OLD_VERSION",
                            kafkaSince[0]);
                    assertOldVersionMonitoring(monitoring, oldKafkaConfig);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2399-03. Kafka load: REQUEST_PARAMS при включенном предрасчете отклоняется и пишется monitoring REQUEST_PARAMS_WITH_PRECALC_ENABLED")
    void kafkaConfigWithRequestParamsShouldWriteRequestParamsWithPrecalcMonitoring(KafkaService kafkaService) {
        long version = SplitterVersionProvider.nextVersion();
        LoadConfigRequestDto kafkaConfig = requestParamsConfig(version, EXP_ID_REQUEST_PARAMS);
        long[] kafkaSince = new long[1];

        getFlowWithRest()
                .step("Отправляем через Kafka конфигурацию с paramSource=REQUEST_PARAMS", flow -> {
                    kafkaSince[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(kafkaConfig);
                })
                .step("Проверяем статус CONFIG_NOT_LOADED и monitoring REQUEST_PARAMS_WITH_PRECALC_ENABLED", flow -> {
                    JsonNode status = kafkaFlow.findStatusByConfigMessageId(kafkaService,
                            kafkaConfig.getMessageId(),
                            kafkaSince[0]);
                    assertStatus(status, kafkaConfig, "CONFIG_NOT_LOADED");
                    assertStatusDescContains(status, "параметрами запроса");

                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            kafkaConfig.getMessageId(),
                            "REQUEST_PARAMS_WITH_PRECALC_ENABLED",
                            kafkaSince[0]);
                    assertRequestParamsWithPrecalcMonitoring(monitoring, kafkaConfig);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2399-04. Kafka load: ошибка валидации конфигурации пишется в status и monitoring VALIDATION_FAILED")
    void kafkaInvalidConfigShouldWriteValidationFailedMonitoring(KafkaService kafkaService) {
        long version = SplitterVersionProvider.nextVersion();
        LoadConfigRequestDto kafkaConfig = invalidNoTrafficRulesConfig(version, EXP_ID_INVALID_CONFIG);
        long[] kafkaSince = new long[1];

        getFlowWithRest()
                .step("Отправляем через Kafka конфигурацию без правил привязки к трафику", flow -> {
                    kafkaSince[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(kafkaConfig);
                })
                .step("Проверяем статус CONFIG_NOT_LOADED и monitoring VALIDATION_FAILED", flow -> {
                    JsonNode status = kafkaFlow.findStatusByConfigMessageId(kafkaService,
                            kafkaConfig.getMessageId(),
                            kafkaSince[0]);
                    assertStatus(status, kafkaConfig, "CONFIG_NOT_LOADED");
                    assertStatusDescContains(status, "валидации");

                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            kafkaConfig.getMessageId(),
                            "VALIDATION_FAILED",
                            kafkaSince[0]);
                    assertValidationFailedMonitoring(monitoring, kafkaConfig, "Нет правил");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2399-05. Kafka load: невалидная структура сообщения пишет monitoring VALIDATION_FAILED")
    void kafkaInvalidMessageStructureShouldWriteValidationFailedMonitoring(KafkaService kafkaService) {
        long version = SplitterVersionProvider.nextVersion();
        String messageId = UUID.randomUUID().toString();
        String invalidPayload = invalidStructureMessage(messageId, version);
        long[] kafkaSince = new long[1];

        getFlowWithRest()
                .step("Отправляем в Kafka сообщение с невалидной DTO-структурой", flow -> {
                    kafkaSince[0] = System.currentTimeMillis();
                    kafkaFlow.sendRaw(messageId, invalidPayload);
                })
                .step("Проверяем monitoring VALIDATION_FAILED по messageId/requestIdIn", flow -> {
                    JsonNode monitoring = kafkaFlow.findMonitoringByMessageIdAndResult(kafkaService,
                            messageId,
                            "VALIDATION_FAILED",
                            kafkaSince[0]);
                    assertInvalidMessageMonitoring(monitoring, messageId);
                })
                .run();
    }
}

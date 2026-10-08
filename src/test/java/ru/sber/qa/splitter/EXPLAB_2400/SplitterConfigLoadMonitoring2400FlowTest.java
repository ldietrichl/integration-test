package ru.sber.qa.splitter.EXPLAB_2400;

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
import ru.sber.qa.splitter.support.RestConfigLoadModeOnly;
import ru.sber.qa.splitter.support.SplitterTestProfileOnly;
import util.support.SplitterVersionProvider;

import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoad2400TestData.VALUE_EXP_1;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoad2400TestData.VALUE_EXP_2;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoad2400TestData.invalidWithoutSaltOrLayerConfig;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoad2400TestData.precalcObject;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoad2400TestData.precalcRequest;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoad2400TestData.requestParamsConfig;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoad2400TestData.singleExperimentConfig;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoad2400TestData.twoExperimentConfig;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoadMonitoring2400Assertions.assertAcceptedLoadResponse;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoadMonitoring2400Assertions.assertLoadResponse;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoadMonitoring2400Assertions.assertLoadResponseDetailsContains;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoadMonitoring2400Assertions.assertLoadedWithPrecalc;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoadMonitoring2400Assertions.assertOldVersion;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoadMonitoring2400Assertions.assertRequestParamsRejected;
import static ru.sber.qa.splitter.EXPLAB_2400.SplitterConfigLoadMonitoring2400Assertions.assertValidationFailed;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@RestConfigLoadModeOnly
public class SplitterConfigLoadMonitoring2400FlowTest extends AbstractConfigLoadMonitoring2400FlowTest {

    private static final int EXP_ID_1 = 240001;
    private static final int EXP_ID_2 = 240002;
    private static final int EXP_ID_NEGATIVE = 240003;

    @Test
    @SplitterTestProfileOnly("monitoring-contract")
    @DisplayName("EXPLAB-2400-01. API load с таблицей предрасчета публикует точные счетчики LOADED_WITH_PRECALC")
    void apiReloadShouldWriteExactPrecalcCounters(KafkaService kafkaService) {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        int soConfigVersion = nextSoConfigVersion();
        LoadConfigRequestDto seedConfig = singleExperimentConfig(versions[0], true, EXP_ID_1, VALUE_EXP_1);
        LoadConfigRequestDto reloadConfig = singleExperimentConfig(versions[1], true, EXP_ID_1, VALUE_EXP_1);
        SplitterPrecalcRequestDto precalc = precalcRequest(soConfigVersion,
                precalcObject(uniqueConfigurationId("01", 1), VALUE_EXP_1),
                precalcObject(uniqueConfigurationId("01", 2), "NO_MATCH"));

        getFlowWithRest()
                .step("Загружаем исходную конфигурацию через API", flow ->
                        assertAcceptedLoadResponse(loadConfig(flow, seedConfig), seedConfig.getConfigVersion()))
                .step("Создаем контролируемую таблицу предрасчета: один объект связан, один без связей", flow ->
                        preCalculate(flow, precalc))
                .step("Повторно загружаем конфигурацию через API и проверяем monitoring", flow -> {
                    ConfigLoadMonitoringCapture2400 capture = loadConfigAndCaptureMonitoring(
                            flow, kafkaService, reloadConfig, "LOADED_WITH_PRECALC");
                    assertAcceptedLoadResponse(capture.response(), reloadConfig.getConfigVersion());

                    assertLoadedWithPrecalc(capture.event(),
                            reloadConfig,
                            capture.startedAtEpochMillis(),
                            soConfigVersion,
                            new ConfigLoadPrecalcCounters2400(1, 2, 1, 1));
                })
                .run();
    }

    @Test
    @SplitterTestProfileOnly("monitoring-contract")
    @DisplayName("EXPLAB-2400-02. Добавление эксперимента обновляет linkedExps и totalExps в monitoring")
    void addedExperimentShouldBeReflectedInMonitoringCounters(KafkaService kafkaService) {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        int soConfigVersion = nextSoConfigVersion();
        LoadConfigRequestDto seedConfig = singleExperimentConfig(versions[0], true, EXP_ID_1, VALUE_EXP_1);
        LoadConfigRequestDto reloadConfig = twoExperimentConfig(
                versions[1], true, EXP_ID_1, VALUE_EXP_1, EXP_ID_2, VALUE_EXP_2);
        SplitterPrecalcRequestDto precalc = precalcRequest(soConfigVersion,
                precalcObject(uniqueConfigurationId("02", 1), VALUE_EXP_1),
                precalcObject(uniqueConfigurationId("02", 2), VALUE_EXP_2));

        getFlowWithRest()
                .step("Загружаем конфигурацию только с первым экспериментом", flow ->
                        assertAcceptedLoadResponse(loadConfig(flow, seedConfig), seedConfig.getConfigVersion()))
                .step("Предрасчитываем два объекта: второй пока остается без связи", flow ->
                        preCalculate(flow, precalc))
                .step("Добавляем второй эксперимент и проверяем пересчитанные счетчики", flow -> {
                    ConfigLoadMonitoringCapture2400 capture = loadConfigAndCaptureMonitoring(
                            flow, kafkaService, reloadConfig, "LOADED_WITH_PRECALC");
                    assertAcceptedLoadResponse(capture.response(), reloadConfig.getConfigVersion());

                    assertLoadedWithPrecalc(capture.event(),
                            reloadConfig,
                            capture.startedAtEpochMillis(),
                            soConfigVersion,
                            new ConfigLoadPrecalcCounters2400(0, 2, 2, 2));
                })
                .run();
    }

    @Test
    @SplitterTestProfileOnly("monitoring-contract")
    @DisplayName("EXPLAB-2400-03. Удаление эксперимента исключает его связи из linkedExps")
    void removedExperimentShouldBeExcludedFromMonitoringCounters(KafkaService kafkaService) {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        int soConfigVersion = nextSoConfigVersion();
        LoadConfigRequestDto seedConfig = twoExperimentConfig(
                versions[0], true, EXP_ID_1, VALUE_EXP_1, EXP_ID_2, VALUE_EXP_2);
        LoadConfigRequestDto reloadConfig = singleExperimentConfig(versions[1], true, EXP_ID_1, VALUE_EXP_1);
        SplitterPrecalcRequestDto precalc = precalcRequest(soConfigVersion,
                precalcObject(uniqueConfigurationId("03", 1), VALUE_EXP_1),
                precalcObject(uniqueConfigurationId("03", 2), VALUE_EXP_2));

        getFlowWithRest()
                .step("Загружаем конфигурацию с двумя экспериментами", flow ->
                        assertAcceptedLoadResponse(loadConfig(flow, seedConfig), seedConfig.getConfigVersion()))
                .step("Создаем таблицу предрасчета с двумя связанными экспериментами", flow ->
                        preCalculate(flow, precalc))
                .step("Удаляем второй эксперимент и проверяем monitoring новой таблицы", flow -> {
                    ConfigLoadMonitoringCapture2400 capture = loadConfigAndCaptureMonitoring(
                            flow, kafkaService, reloadConfig, "LOADED_WITH_PRECALC");
                    assertAcceptedLoadResponse(capture.response(), reloadConfig.getConfigVersion());

                    assertLoadedWithPrecalc(capture.event(),
                            reloadConfig,
                            capture.startedAtEpochMillis(),
                            soConfigVersion,
                            new ConfigLoadPrecalcCounters2400(1, 2, 1, 1));
                })
                .run();
    }

    @Test
    @SplitterTestProfileOnly("monitoring-contract")
    @DisplayName("EXPLAB-2400-04. forceConfigLoad=true загружает старую версию и публикует LOADED_WITH_PRECALC")
    void forcedOldVersionShouldWriteLoadedWithPrecalcMonitoring(KafkaService kafkaService) {
        long currentVersion = SplitterVersionProvider.nextVersion();
        long forcedOldVersion = SplitterVersionProvider.nextOlderThan(currentVersion);
        int soConfigVersion = nextSoConfigVersion();
        LoadConfigRequestDto currentConfig = singleExperimentConfig(currentVersion, true, EXP_ID_1, VALUE_EXP_1);
        LoadConfigRequestDto forcedOldConfig = singleExperimentConfig(
                forcedOldVersion, true, EXP_ID_1, VALUE_EXP_1);
        SplitterPrecalcRequestDto precalc = precalcRequest(soConfigVersion,
                precalcObject(uniqueConfigurationId("04", 1), VALUE_EXP_1),
                precalcObject(uniqueConfigurationId("04", 2), "NO_MATCH"));

        getFlowWithRest()
                .step("Загружаем актуальную конфигурацию", flow ->
                        assertAcceptedLoadResponse(loadConfig(flow, currentConfig), currentVersion))
                .step("Создаем таблицу предрасчета", flow ->
                        preCalculate(flow, precalc))
                .step("Принудительно загружаем старую версию и проверяем monitoring", flow -> {
                    ConfigLoadMonitoringCapture2400 capture = loadConfigAndCaptureMonitoring(
                            flow, kafkaService, forcedOldConfig, "LOADED_WITH_PRECALC");
                    assertAcceptedLoadResponse(capture.response(), forcedOldVersion);
                    assertLoadedWithPrecalc(capture.event(),
                            forcedOldConfig,
                            capture.startedAtEpochMillis(),
                            soConfigVersion,
                            new ConfigLoadPrecalcCounters2400(1, 2, 1, 1));
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2400-05. Старая версия через API публикует NOT_LOADED_OLD_VERSION")
    void oldVersionShouldWriteMonitoring(KafkaService kafkaService) {
        long currentVersion = SplitterVersionProvider.nextVersion();
        long oldVersion = SplitterVersionProvider.nextOlderThan(currentVersion);
        LoadConfigRequestDto currentConfig = singleExperimentConfig(currentVersion, true, EXP_ID_1, VALUE_EXP_1);
        LoadConfigRequestDto oldConfig = singleExperimentConfig(oldVersion, false, EXP_ID_2, VALUE_EXP_2);

        getFlowWithRest()
                .step("Принудительно устанавливаем текущую версию конфигурации", flow ->
                        assertAcceptedLoadResponse(loadConfig(flow, currentConfig), currentVersion))
                .step("Загружаем старую версию с forceConfigLoad=false и проверяем monitoring", flow -> {
                    ConfigLoadMonitoringCapture2400 capture = loadConfigAndCaptureMonitoring(
                            flow, kafkaService, oldConfig, "NOT_LOADED_OLD_VERSION");
                    assertLoadResponse(capture.response(), "OLD_VERSION", currentVersion);
                    assertOldVersion(capture.event(), oldConfig, capture.startedAtEpochMillis(), currentVersion);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2400-06. REQUEST_PARAMS при предрасчете публикует REQUEST_PARAMS_WITH_PRECALC_ENABLED")
    void requestParamsShouldWriteRejectedMonitoring(KafkaService kafkaService) {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        LoadConfigRequestDto currentConfig = singleExperimentConfig(versions[0], true, EXP_ID_1, VALUE_EXP_1);
        LoadConfigRequestDto requestParamsConfig = requestParamsConfig(versions[1], EXP_ID_NEGATIVE);

        getFlowWithRest()
                .step("Устанавливаем валидную текущую конфигурацию", flow ->
                        assertAcceptedLoadResponse(loadConfig(flow, currentConfig), currentConfig.getConfigVersion()))
                .step("Отправляем конфигурацию с REQUEST_PARAMS и проверяем monitoring", flow -> {
                    ConfigLoadMonitoringCapture2400 capture = loadConfigAndCaptureMonitoring(
                            flow, kafkaService, requestParamsConfig, "REQUEST_PARAMS_WITH_PRECALC_ENABLED");
                    assertLoadResponse(capture.response(),
                            "REQUEST_PARAMS_WITH_PRECALC_NOT_SUPPORTED",
                            currentConfig.getConfigVersion());
                    assertRequestParamsRejected(
                            capture.event(),
                            requestParamsConfig,
                            capture.startedAtEpochMillis(),
                            currentConfig.getConfigVersion());
                })
                .run();
    }

    @Test
    @SplitterTestProfileOnly("monitoring-contract")
    @DisplayName("EXPLAB-2400-07. Ошибка валидации через API публикует VALIDATION_FAILED с resultDetails")
    void validationErrorShouldWriteMonitoring(KafkaService kafkaService) {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        LoadConfigRequestDto currentConfig = singleExperimentConfig(versions[0], true, EXP_ID_1, VALUE_EXP_1);
        LoadConfigRequestDto invalidConfig = invalidWithoutSaltOrLayerConfig(versions[1], EXP_ID_NEGATIVE);

        getFlowWithRest()
                .step("Устанавливаем валидную текущую конфигурацию", flow ->
                        assertAcceptedLoadResponse(loadConfig(flow, currentConfig), currentConfig.getConfigVersion()))
                .step("Отправляем эксперимент без соли и слоя и проверяем monitoring", flow -> {
                    ConfigLoadMonitoringCapture2400 capture = loadConfigAndCaptureMonitoring(
                            flow, kafkaService, invalidConfig, "VALIDATION_FAILED");
                    assertLoadResponse(capture.response(), "CONFIG_ERROR", currentConfig.getConfigVersion());
                    assertLoadResponseDetailsContains(capture.response(), "salt");

                    assertValidationFailed(capture.event(),
                            invalidConfig,
                            capture.startedAtEpochMillis(),
                            currentConfig.getConfigVersion(),
                            capture.response().getResultDetails());
                })
                .run();
    }
}

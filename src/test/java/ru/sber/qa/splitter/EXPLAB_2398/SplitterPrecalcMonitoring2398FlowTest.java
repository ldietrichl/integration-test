package ru.sber.qa.splitter.EXPLAB_2398;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterPrecalcMonitoring2398FlowTest extends AbstractPrecalcMonitoring2398FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2398-PC-01. Повторный pre-calculate пишет LOADED и счетчики copied/added/deleted")
    void repeatedPrecalcShouldWriteLoadedMonitoringWithStableCounters(KafkaService kafkaService) {
        LoadConfigRequestDto config = oneExperimentConfig();
        int soVersion = nextSoConfigVersion();
        String matchingUc = uc("pc01", 1);
        String nonMatchingUc = uc("pc01", 2);

        SplitterPrecalcRequestDto seed = precalcRequest(soVersion,
                matchingPrecalcObject(matchingUc),
                nonMatchingPrecalcObject(nonMatchingUc));
        SplitterPrecalcRequestDto repeated = precalcRequest(soVersion,
                matchingPrecalcObject(matchingUc),
                nonMatchingPrecalcObject(nonMatchingUc));
        SplitRequestDto splitAfterPrecalc = splitByPrecalcOnlyRequest("EXPLAB-2398-PC-01",
                splitObjectByPrecalcOnly(matchingUc, SPLIT_OBJECT_ID_1),
                splitObjectByPrecalcOnly(nonMatchingUc, SPLIT_OBJECT_ID_2));

        getFlowWithRest()
                .step("Загружаем splitter config с одним экспериментом и одним условием", flow ->
                        loadConfig(flow, config))
                .step("Seed pre-calculate фиксирует контролируемое состояние таблицы предрасчета", flow ->
                        calculatePreliminary(flow, seed))
                .step("Повторный pre-calculate по тому же полному списку должен дать стабильные счетчики", flow -> {
                    long since = System.currentTimeMillis();
                    calculatePreliminary(flow, repeated);
                    assertMonitoringEvent(kafkaService, since, loadedExpectation(repeated,
                            2, 0, 0,
                            1, 2,
                            1, 1));
                })
                .step("Split после pre-calculate использует связи по uniqueConfigurationId", flow -> {
                    ValidatableResponseWrapper response = split(flow, splitAfterPrecalc);
                    assertObjectHasMainAndAll(response, SPLIT_OBJECT_ID_1);
                    assertMainExp(response, SPLIT_OBJECT_ID_1, EXP_ID);
                    assertObjectResultsEmpty(response, SPLIT_OBJECT_ID_2);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2398-PC-02. Полный список объектов отражает добавление и удаление в monitoring-счетчиках")
    void exhaustiveObjectListShouldWriteAddedDeletedCounters(KafkaService kafkaService) {
        LoadConfigRequestDto config = oneExperimentConfig();
        int soVersion = nextSoConfigVersion();
        String a = uc("pc02", 1);
        String b = uc("pc02", 2);
        String c = uc("pc02", 3);

        SplitterPrecalcRequestDto seed = precalcRequest(soVersion,
                matchingPrecalcObject(a),
                matchingPrecalcObject(b));
        SplitterPrecalcRequestDto changedFullList = precalcRequest(soVersion,
                matchingPrecalcObject(a),
                matchingPrecalcObject(c));

        getFlowWithRest()
                .step("Загружаем splitter config", flow ->
                        loadConfig(flow, config))
                .step("Seed pre-calculate: в таблице есть A и B", flow ->
                        calculatePreliminary(flow, seed))
                .step("Новый исчерпывающий список содержит A и C: B должен считаться удаленным, C добавленным", flow -> {
                    long since = System.currentTimeMillis();
                    calculatePreliminary(flow, changedFullList);
                    assertMonitoringEvent(kafkaService, since, loadedExpectation(changedFullList,
                            1, 1, 1,
                            0, 2,
                            1, 1));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2398-PC-03. notLinkedObjects и linkedExps считаются по фактическим связям предрасчета")
    void notLinkedObjectsAndLinkedExpsShouldBeCalculatedFromPrecalcLinks(KafkaService kafkaService) {
        LoadConfigRequestDto config = oneExperimentConfig();
        int soVersion = nextSoConfigVersion();
        String first = uc("pc03", 1);
        String second = uc("pc03", 2);

        SplitterPrecalcRequestDto seed = precalcRequest(soVersion,
                nonMatchingPrecalcObject(first),
                nonMatchingPrecalcObject(second));
        SplitterPrecalcRequestDto repeated = precalcRequest(soVersion,
                nonMatchingPrecalcObject(first),
                nonMatchingPrecalcObject(second));

        getFlowWithRest()
                .step("Загружаем splitter config с условием segment2398=YES", flow ->
                        loadConfig(flow, config))
                .step("Seed pre-calculate: оба объекта не подходят под условие", flow ->
                        calculatePreliminary(flow, seed))
                .step("Повторный pre-calculate должен показать totalObjects=2, notLinkedObjects=2, linkedExps=0", flow -> {
                    long since = System.currentTimeMillis();
                    calculatePreliminary(flow, repeated);
                    assertMonitoringEvent(kafkaService, since, loadedExpectation(repeated,
                            2, 0, 0,
                            2, 2,
                            0, 1));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2398-PC-04. Monitoring-событие коррелируется с requestIdIn и soConfigVersion")
    void monitoringEventShouldBeCorrelatedByRequestIdAndSoConfigVersion(KafkaService kafkaService) {
        LoadConfigRequestDto config = oneExperimentConfig();
        int soVersion = nextSoConfigVersion();
        String first = uc("pc04", 1);

        SplitterPrecalcRequestDto seed = precalcRequest(soVersion, matchingPrecalcObject(first));
        SplitterPrecalcRequestDto repeated = precalcRequest(soVersion, matchingPrecalcObject(first));

        getFlowWithRest()
                .step("Загружаем splitter config", flow ->
                        loadConfig(flow, config))
                .step("Seed pre-calculate создает контролируемую таблицу", flow ->
                        calculatePreliminary(flow, seed))
                .step("Проверяем корреляцию monitoring event по requestIdIn/soConfigVersion и базовый контракт", flow -> {
                    long since = System.currentTimeMillis();
                    calculatePreliminary(flow, repeated);
                    assertMonitoringEvent(kafkaService, since, loadedExpectation(repeated,
                            1, 0, 0,
                            0, 1,
                            1, 1));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2398-PC-05. Ошибка валидации pre-calculate пишет VALIDATION_FAILED в monitoring")
    void validationFailedShouldWriteMonitoringEvent(KafkaService kafkaService) {
        int soVersion = nextSoConfigVersion();
        SplitterPrecalcRequestDto invalidRequest = SplitterPrecalcRequestDto.builder()
                .requestId(java.util.UUID.randomUUID().toString())
                .soConfigVersion(soVersion)
                .splittingObjects(List.of(dto.splitter.precalc.SplitterPrecalcObjectDto.builder()
                        .uniqueConfigurationId(null)
                        .objectParams(List.of(new dto.splitter.precalc.SplitterPrecalcParamDto(
                                CONDITION_PARAM_CODE, List.of(MATCHING_VALUE), "STRING")))
                        .build()))
                .build();

        getFlowWithRest()
                .step("Отправляем структурно невалидный pre-calculate: отсутствует mandatory uniqueConfigurationId", flow -> {
                    long since = System.currentTimeMillis();
                    ValidatableResponseWrapper response = flow.restCustomSteps().splitterSteps().calculatePreliminary(invalidRequest);
                    response.should(haveStatusCode(HttpStatus.SC_BAD_REQUEST));
                    assertMonitoringEvent(kafkaService, since, validationFailedExpectation(invalidRequest));
                })
                .run();
    }

}

package ru.sber.qa.splitter.EXPLAB_2603;

import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import flow.RestCustomFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import util.support.SplitterVersionProvider;

/**
 * Проверяет режим SPLITTER_EMPTY_OBJECTS_RESPONSE_ENABLED=false.
 */
class SplitterEmptyObjectsResponseDisabled2603FlowTest extends AbstractEmptyObjectsResponse2603FlowTest {

    @Override
    protected EndpointMode endpointMode() {
        return EndpointMode.EMPTY_OBJECTS_EXCLUDED;
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2603-02. Объекты без экспериментов исключаются из ответа split при выключенной настройке")
    void emptyObjectsShouldBeExcludedFromSplitResponseWhenSettingDisabled(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = emptyObjectsConfig(version);
        SplitRequestDto request = mixedSplitRequest("EXPLAB-2603-DISABLED-MIXED-" + version);
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2603: один объект связывается с экспериментом, два объекта остаются без экспериментов", flow -> loadConfig(flow, config))
                .step("Выполняем split и проверяем, что API-ответ содержит только объект с результатом", flow -> {
                    var response = split((RestCustomFlow) flow, request);
                    assertResponseRequestId(response, request);
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 1);
                    assertResponseObjectIdsExactly(response, MATCHED_OBJECT_ID);
                    assertObjectHasNonEmptyResults(response, MATCHED_OBJECT_ID);
                    assertObjectHasExpIdAnywhere(response, MATCHED_OBJECT_ID, EXP_ID);
                    assertObjectAbsent(response, EMPTY_OBJECT_ID_1);
                    assertObjectAbsent(response, EMPTY_OBJECT_ID_2);
                })
                .step("Проверяем, что КАП-сообщение содержит полный результат исходного split-запроса", flow ->
                        assertKapPayloadContainsRequestAndObjects(kafkaService, request, since,
                                MATCHED_OBJECT_ID, EMPTY_OBJECT_ID_1, EMPTY_OBJECT_ID_2))
                .step("Проверяем, что в monitoring topic нет ошибки отправки результата в КАП", flow ->
                        assertNoKapNotSentMonitoringEvent(kafkaService, request, since))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2603-04. Если все объекты без экспериментов, ответ split пустой при выключенной настройке")
    void onlyEmptyObjectsShouldProduceEmptySplitResponseWhenSettingDisabled(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = emptyObjectsConfig(version);
        SplitRequestDto request = onlyEmptyObjectsSplitRequest("EXPLAB-2603-DISABLED-EMPTY-" + version);
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2603 с одним условием привязки", flow -> loadConfig(flow, config))
                .step("Выполняем split только с объектами без экспериментов и проверяем пустой API-ответ", flow -> {
                    var response = split((RestCustomFlow) flow, request);
                    assertResponseRequestId(response, request);
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 0);
                    assertResponseObjectIdsExactly(response);
                    assertObjectAbsent(response, EMPTY_OBJECT_ID_1);
                    assertObjectAbsent(response, EMPTY_OBJECT_ID_2);
                })
                .step("Проверяем, что КАП-сообщение содержит полный результат исходного split-запроса", flow ->
                        assertKapPayloadContainsRequestAndObjects(kafkaService, request, since,
                                EMPTY_OBJECT_ID_1, EMPTY_OBJECT_ID_2))
                .step("Проверяем, что в monitoring topic нет ошибки отправки результата в КАП", flow ->
                        assertNoKapNotSentMonitoringEvent(kafkaService, request, since))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2603-06. Объект с экспериментом возвращается без изменений при выключенной настройке")
    void matchedObjectShouldBeReturnedWithoutChangesWhenSettingDisabled(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = emptyObjectsConfig(version);
        SplitRequestDto request = onlyMatchedObjectSplitRequest("EXPLAB-2603-DISABLED-MATCHED-" + version);
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2603 с одним условием привязки", flow -> loadConfig(flow, config))
                .step("Выполняем split только с объектом, связанным с экспериментом", flow -> {
                    var response = split((RestCustomFlow) flow, request);
                    assertResponseRequestId(response, request);
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 1);
                    assertResponseObjectIdsExactly(response, MATCHED_OBJECT_ID);
                    assertObjectHasNonEmptyResults(response, MATCHED_OBJECT_ID);
                    assertObjectHasExpIdAnywhere(response, MATCHED_OBJECT_ID, EXP_ID);
                })
                .step("Проверяем, что КАП-сообщение содержит объект исходного split-запроса", flow ->
                        assertKapPayloadContainsRequestAndObjects(kafkaService, request, since, MATCHED_OBJECT_ID))
                .step("Проверяем, что в monitoring topic нет ошибки отправки результата в КАП", flow ->
                        assertNoKapNotSentMonitoringEvent(kafkaService, request, since))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2603-08. Единственный объект без эксперимента исключается при выключенной настройке")
    void singleEmptyObjectShouldBeExcludedWhenSettingDisabled(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = emptyObjectsConfig(version);
        SplitRequestDto request = singleEmptyObjectSplitRequest("EXPLAB-2603-DISABLED-SINGLE-EMPTY-" + version);
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2603 с одним условием привязки", flow -> loadConfig(flow, config))
                .step("Выполняем split с единственным объектом без экспериментов и проверяем пустой API-ответ", flow -> {
                    var response = split((RestCustomFlow) flow, request);
                    assertResponseRequestId(response, request);
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 0);
                    assertResponseObjectIdsExactly(response);
                    assertObjectAbsent(response, EMPTY_OBJECT_ID_1);
                })
                .step("Проверяем, что КАП-сообщение содержит объект исходного split-запроса", flow ->
                        assertKapPayloadContainsRequestAndObjects(kafkaService, request, since, EMPTY_OBJECT_ID_1))
                .step("Проверяем, что в monitoring topic нет ошибки отправки результата в КАП", flow ->
                        assertNoKapNotSentMonitoringEvent(kafkaService, request, since))
                .run();
    }
}

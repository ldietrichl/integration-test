package ru.sber.qa.splitter.EXPLAB_2603;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import ru.sber.qa.splitter.support.SplitterTestProfileOnly;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import flow.RestCustomFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.kafka.KafkaService;
import util.support.SplitterVersionProvider;

/**
 * Проверяет режим SPLITTER_EMPTY_OBJECTS_RESPONSE_ENABLED=true.
 */
@AnyConfigLoadMode
@SplitterTestProfileOnly({"current", "empty-objects-enabled"})
class SplitterEmptyObjectsResponseEnabled2603FlowTest extends AbstractEmptyObjectsResponse2603FlowTest {

    @Override
    protected EndpointMode endpointMode() {
        return EndpointMode.EMPTY_OBJECTS_INCLUDED;
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2603-01. Объекты без экспериментов возвращаются в ответе split при включенной настройке")
    void emptyObjectsShouldBeReturnedInSplitResponseWhenSettingEnabled(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = emptyObjectsConfig(version);
        SplitRequestDto request = mixedSplitRequest("EXPLAB-2603-ENABLED-MIXED-" + version);
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2603: один объект связывается с экспериментом, два объекта остаются без экспериментов", flow -> loadConfig(flow, config))
                .step("Выполняем split и проверяем, что в API-ответе остались и объект с результатом, и пустые объекты", flow -> {
                    var response = split((RestCustomFlow) flow, request);
                    assertResponseRequestId(response, request);
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 3);
                    assertResponseObjectIdsExactly(response, MATCHED_OBJECT_ID, EMPTY_OBJECT_ID_1, EMPTY_OBJECT_ID_2);
                    assertObjectHasNonEmptyResults(response, MATCHED_OBJECT_ID);
                    assertObjectHasExpIdAnywhere(response, MATCHED_OBJECT_ID, EXP_ID);
                    assertObjectWithoutExperimentIsEmpty(response, EMPTY_OBJECT_ID_1);
                    assertObjectWithoutExperimentIsEmpty(response, EMPTY_OBJECT_ID_2);
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
    @DisplayName("EXPLAB-2603-03. Только объекты без экспериментов возвращаются пустыми в ответе split при включенной настройке")
    void onlyEmptyObjectsShouldBeReturnedInSplitResponseWhenSettingEnabled(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = emptyObjectsConfig(version);
        SplitRequestDto request = onlyEmptyObjectsSplitRequest("EXPLAB-2603-ENABLED-EMPTY-" + version);
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2603 с одним условием привязки", flow -> loadConfig(flow, config))
                .step("Выполняем split только с объектами без экспериментов и проверяем, что API-ответ содержит пустые объекты", flow -> {
                    var response = split((RestCustomFlow) flow, request);
                    assertResponseRequestId(response, request);
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 2);
                    assertResponseObjectIdsExactly(response, EMPTY_OBJECT_ID_1, EMPTY_OBJECT_ID_2);
                    assertObjectWithoutExperimentIsEmpty(response, EMPTY_OBJECT_ID_1);
                    assertObjectWithoutExperimentIsEmpty(response, EMPTY_OBJECT_ID_2);
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
    @DisplayName("EXPLAB-2603-05. Объект с экспериментом возвращается без изменений при включенной настройке")
    void matchedObjectShouldBeReturnedWithoutChangesWhenSettingEnabled(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = emptyObjectsConfig(version);
        SplitRequestDto request = onlyMatchedObjectSplitRequest("EXPLAB-2603-ENABLED-MATCHED-" + version);
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
    @DisplayName("EXPLAB-2603-07. Единственный объект без эксперимента возвращается пустым при включенной настройке")
    void singleEmptyObjectShouldBeReturnedWhenSettingEnabled(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = emptyObjectsConfig(version);
        SplitRequestDto request = singleEmptyObjectSplitRequest("EXPLAB-2603-ENABLED-SINGLE-EMPTY-" + version);
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2603 с одним условием привязки", flow -> loadConfig(flow, config))
                .step("Выполняем split с единственным объектом без экспериментов", flow -> {
                    var response = split((RestCustomFlow) flow, request);
                    assertResponseRequestId(response, request);
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 1);
                    assertResponseObjectIdsExactly(response, EMPTY_OBJECT_ID_1);
                    assertObjectWithoutExperimentIsEmpty(response, EMPTY_OBJECT_ID_1);
                })
                .step("Проверяем, что КАП-сообщение содержит объект исходного split-запроса", flow ->
                        assertKapPayloadContainsRequestAndObjects(kafkaService, request, since, EMPTY_OBJECT_ID_1))
                .step("Проверяем, что в monitoring topic нет ошибки отправки результата в КАП", flow ->
                        assertNoKapNotSentMonitoringEvent(kafkaService, request, since))
                .run();
    }
}

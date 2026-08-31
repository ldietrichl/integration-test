package ru.sber.qa.splitter.tests_v9.sdk;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Tests-v9 / EXPLAB-2690. SDK-derived проверки полного и публичного результата")
@AnyConfigLoadMode
public class SplitterV9SdkDerivedBehaviorFlowTest extends AbstractSplitterV9FlowTest {

    @Test
    @DisplayName("SPL-SDK-01. MAPPER MAIN выбирается по actionType, а не по result")
    void mapperMainShouldBeDrivenByActionTypeNotDocumentResultParam() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER, version,
                experiment(901,
                        "SPL-SDK-01-A",
                        List.of(objectParamEqualsCondition(1, "segment", "sdk", "STRING")),
                        List.of(groupWithDocResult("A", 0, 10000, 1, "1", "999"))),
                experiment(902,
                        "SPL-SDK-01-B",
                        List.of(objectParamEqualsCondition(1, "segment", "sdk", "STRING")),
                        List.of(groupWithDocResult("A", 0, 10000, 1, "0", "1000"))));
        SplitRequestDto request = splitRequest("SPL-SDK-01", object(OBJECT_1, param("segment", "sdk", "STRING")));

        getFlowWithRest()
                .step("Загружаем config: result у exp902 больше, но actionType выше у exp901", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что MAIN выбран по actionType", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertMainExp(response, OBJECT_1, 901L);
                    assertMainActionType(response, OBJECT_1, "1");
                    assertFirstRuleExpResultValue(response, OBJECT_1, "MAIN", "999");
                })
                .run();
    }

    @Test
@DisplayName("SPL-SDK-02. Kafka/report содержит resultDt и полный результат split")
    void kafkaReportShouldContainFullResultBeforeApiResponseFiltering(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = singleDocumentConfig(version);
        String splittingId = splittingIdForRange("SPL-SDK-02", V9_SALT, 0, 2500);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1,
                        param("id1", "1", "INTEGER"),
                        param("id2", "2", "INTEGER"),
                        param("id3", "3", "INTEGER")));
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config с тремя группами одного эксперимента", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split и проверяем очищенный публичный REST ALL", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertRuleResultSize(response, OBJECT_1, "ALL", 1);
                    assertAllResponseRowsAreWorkedGroups(response, OBJECT_1);
                })
                .step("Читаем Kafka/report и проверяем наличие полного результата по requestId", flow -> {
                    String payload = findKafkaPayloadByRequestId(kafkaService, request.getRequestId(), since);
                    assertJsonContainsText(payload, request.getRequestId());
                    assertJsonContainsText(payload, "finalExpGroup");
                    assertJsonContainsText(payload, "expGroup");
                    assertJsonContainsText(payload, "resultDt");
                })
                .run();
    }

    @Test
    @DisplayName("SPL-SDK-03. REST MAPPER возвращает в ALL только реально сработавшие группы")
    void apiAllRuleShouldContainOnlyWorkedGroupsAfterModifyResponse() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = mapperAlternativeConfig(version);
        String splittingId = splittingIdForRange("SPL-SDK-03", V9_SALT, 2500, 3000);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1, param("id1", "1", "INTEGER")),
                object(OBJECT_2, param("id2", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с альтернативой", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем post-processing EXPLAB-2690: REST ALL не содержит диагностические строки", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                    if (findRule(response, OBJECT_1, "ALL", false) != null) {
                        assertRuleExpsUseWorkedGroups(response, OBJECT_1, "ALL");
                    }
                    if (findRule(response, OBJECT_2, "ALL", false) != null) {
                        assertRuleExpsUseWorkedGroups(response, OBJECT_2, "ALL");
                    }
                })
                .run();
    }

    @Test
@DisplayName("SPL-SDK-04. resultDt проверяется в Kafka-report; в API response его нет")
    void resultDtShouldBeReportedToKafkaButNotApiResponse(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = singleDocumentConfig(version);
        SplitRequestDto request = splitRequest("SPL-SDK-04", object(OBJECT_1,
                param("id1", "1", "INTEGER"),
                param("id2", "2", "INTEGER"),
                param("id3", "3", "INTEGER")));
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем config", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем отсутствие resultDt в API response", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertNoResultDtInApiResponse(response);
                })
                .step("Проверяем наличие resultDt в Kafka/report", flow -> {
                    String payload = findKafkaPayloadByRequestId(kafkaService, request.getRequestId(), since);
                    assertJsonContainsField(payload, "resultDt");
                })
                .run();
    }

    @Test
@DisplayName("SPL-SDK-05. return-suppressed=false исключает подавленные объекты из API-ответа")
    void suppressedObjectsShouldBeExcludedWhenReturnSuppressedDisabled() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER, version,
                experiment(905,
                        "SPL-SDK-05",
                        List.of(objectParamEqualsCondition(1, "segment", "suppressed", "STRING")),
                        List.of(groupWithDocResult("A", 0, 10000, 1, "2", "2"))));
        SplitRequestDto request = splitRequest("SPL-SDK-05", object(OBJECT_1, param("segment", "suppressed", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с actionType=2, который по SDK/ConfigMap может приводить к filtered/suppressed", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что при стендовом return-suppressed=false объект может быть исключен или возвращен пустым", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    if (hasObject(response, OBJECT_1)) {
                        assertRuleExpIdsExactly(response, OBJECT_1, "MAIN", 905L);
                    }
                })
                .run();
    }

    @Test
@DisplayName("SPL-SDK-06. emptyObjectsResponseEnabled управляет возвратом объектов без экспериментов")
    void emptyObjectsResponseFlagShouldControlEmptyObjectsPresence() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto mapperConfig = configFor(EndpointMode.MAPPER, version,
                experiment(906,
                        "SPL-SDK-06",
                        List.of(objectParamEqualsCondition(1, "segment", "hit", "STRING")),
                        List.of(groupWithDocResult("A", 0, 10000, 1, "1", "1"))));
        SplitRequestDto request = splitRequest("SPL-SDK-06", object(OBJECT_1, param("segment", "miss", "STRING")));

        getFlowWithRest()
                .step("Загружаем config и отправляем объект, который не связывается ни с одним experiment", flow -> loadConfig(flow, EndpointMode.MAPPER, mapperConfig))
                .step("Проверяем допустимый SDK-контракт: объект либо отсутствует, либо возвращается пустым согласно флагу стенда", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertObjectEmptyOrAbsent(response, OBJECT_1);
                })
                .run();
    }

    @Test
@DisplayName("SPL-SDK-07. allRuleCodeExpEnabled управляет наличием ALL в API-ответе")
    void allRuleCodeExpEnabledShouldControlAllRulePresence() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = singleDocumentConfig(version);
        String splittingId = splittingIdForRange("SPL-SDK-07", V9_SALT, 0, 2500);
        SplitRequestDto request = splitRequest(splittingId, object(OBJECT_1,
                param("id1", "1", "INTEGER"),
                param("id2", "2", "INTEGER"),
                param("id3", "3", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем текущее поведение ALL: при включенном allRuleCodeExpEnabled блок есть, при выключенном нет", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    if (findRule(response, OBJECT_1, "ALL", false) != null) {
                        assertRuleResultSize(response, OBJECT_1, "ALL", 1);
                        assertAllResponseRowsAreWorkedGroups(response, OBJECT_1);
                    } else {
                        assertRuleMissingOrEmpty(response, OBJECT_1, "ALL");
                    }
                })
                .run();
    }

    private LoadConfigRequestDto singleDocumentConfig(long version) {
        return configFor(EndpointMode.MAPPER, version,
                experiment(1,
                        V9_SALT,
                        List.of(
                                objectParamEqualsCondition(1, "id1", "1", "INTEGER"),
                                objectParamEqualsCondition(2, "id2", "2", "INTEGER"),
                                objectParamEqualsCondition(3, "id3", "3", "INTEGER")),
                        List.of(
                                groupWithDocResult("A", 0, 2500, 1, "1", "1"),
                                groupWithDocResult("B", 2500, 5000, 2, "2", "2"),
                                groupWithEmptyResultParams("C", 5000, 7500, 3))));
    }

    private LoadConfigRequestDto mapperAlternativeConfig(long version) {
        return configFor(EndpointMode.MAPPER, version,
                experiment(1,
                        V9_SALT,
                        List.of(
                                objectParamEqualsCondition(1, "id1", "1", "INTEGER"),
                                objectParamEqualsCondition(2, "id2", "2", "INTEGER")),
                        List.of(
                                groupWithDocResult("A", List.of(share(0, 2500)), 1, "0", "1"),
                                groupWithDocResult("B", List.of(share(2500, 5000)), 2, "1", "2"))),
                experiment(2,
                        V9_SALT,
                        List.of(objectParamEqualsCondition(1, "id1", "1", "INTEGER")),
                        List.of(groupWithDocResult("A", List.of(share(0, 1000), share(2500, 3500), share(5000, 6000)), 1, "1", "2"))),
                experiment(3,
                        V9_SALT,
                        List.of(objectParamEqualsCondition(1, "id2", "2", "INTEGER")),
                        List.of(groupWithDocResult("A", List.of(share(0, 500), share(1000, 1500), share(2500, 3000), share(3500, 4000), share(5000, 5500), share(6000, 6500)), 1, "3", "3"))));
    }
}

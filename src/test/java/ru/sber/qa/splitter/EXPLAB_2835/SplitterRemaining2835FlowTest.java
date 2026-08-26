package ru.sber.qa.splitter.EXPLAB_2835;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
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
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2835. Split: remaining functional plan")
public class SplitterRemaining2835FlowTest extends AbstractSplitterV9FlowTest {

    private static final AtomicInteger SO_VERSION = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
    private static final String SALT = "EXPLAB-2835-REMAINING-SALT";
    private static final String OBJECT = "explab-2835-object";
    private static final String OBJECT_2 = "explab-2835-object-2";
    private static final String OBJECT_3 = "explab-2835-object-3";

    @Test
    @DisplayName("EXPLAB-2835-SPL-01. Валидный split возвращает базовый контракт, MAIN и ALL")
    void validSplitShouldReturnBasicContractMainAndAll() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28350101, "SPL-01", "0"));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-01",
                objectWithUniqueId("explab-2835-spl-01-uid", OBJECT, param("marker", "SPL-01", "STRING")));

        getFlowWithRest()
                .step("Загружаем валидный MAPPER config", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split и проверяем envelope, MAIN, ALL", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertSplittingResultsHaveUniqueObjectIds(response);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28350101L, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT, "ALL", 28350101L);
                    assertRuleExpsHaveMandatoryFields(response, OBJECT, "MAIN");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-02. Объект без matching condition возвращается пустым или отсутствует")
    void unmatchedObjectShouldHaveEmptyOrAbsentResults() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28350201, "MATCH", "0"));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-02",
                objectWithUniqueId("explab-2835-spl-02-uid", OBJECT, param("marker", "NO_MATCH", "STRING")));

        getFlowWithRest()
                .step("Загружаем config, которому объект не соответствует", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем REST cleanup для unmatched object", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertObjectEmptyOrAbsent(response, OBJECT);
                })
                .run();
    }

    @Disabled("Требуется изолированный чистый стенд без загруженного config")
    @Test
    @DisplayName("EXPLAB-2835-SPL-03. Manual/env: split без config возвращает NO_SPLIT_CONFIG")
    void splitWithoutLoadedConfigShouldReturnNoSplitConfig() {
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-06. AND внутри блока требует выполнения всех выражений")
    void andInsideRuleBlockShouldRequireAllExpressions() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto condition = condition(1, List.of(List.of(
                rule("INTEGER", "channelId", "SPLITTING_OBJECTS", "equal", "20"),
                rule("STRING", "productId", "SPLITTING_OBJECTS", "equal", "P1"))));
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                experiment(28350601, SALT, List.of(condition), List.of(actionGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-06",
                objectWithUniqueId("explab-2835-spl-06-positive", OBJECT,
                        param("channelId", "20", "INTEGER"),
                        param("productId", "P1", "STRING")),
                objectWithUniqueId("explab-2835-spl-06-negative", OBJECT_2,
                        param("channelId", "20", "INTEGER"),
                        param("productId", "P2", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с AND-блоком", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем positive и negative объекты", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28350601L, "A", "A");
                    assertObjectEmptyOrAbsent(response, OBJECT_2);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-07. OR между блоками матчит объект по любому блоку")
    void orBetweenRuleBlocksShouldMatchAnyBlock() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto condition = condition(1, List.of(
                List.of(rule("INTEGER", "channelId", "SPLITTING_OBJECTS", "equal", "20")),
                List.of(rule("STRING", "productId", "SPLITTING_OBJECTS", "equal", "P2")),
                List.of(rule("INTEGER", "templateId", "SPLITTING_OBJECTS", "equal", "777"))));
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                experiment(28350701, SALT, List.of(condition), List.of(actionGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-07",
                objectWithUniqueId("explab-2835-spl-07-uid", OBJECT, param("productId", "P2", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с несколькими OR-блоками", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что срабатывание второго OR-блока достаточно", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28350701L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-08. Матрица типов и операторов INTEGER/STRING/BOOLEAN")
    void dataTypesAndCoreOperatorsShouldBeApplied() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                operatorExperiment(28350801, rule("INTEGER", "score", "SPLITTING_OBJECTS", "more_equal", "10")),
                operatorExperiment(28350802, rule("NUMBER", "amount", "SPLITTING_OBJECTS", "less", "20.5")),
                operatorExperiment(28350803, rule("STRING", "status", "SPLITTING_OBJECTS", "in", "OK", "WARN")),
                operatorExperiment(28350804, rule("BOOLEAN", "enabled", "SPLITTING_OBJECTS", "equal", "true")),
                operatorExperiment(28350805, rule("DATE", "date", "SPLITTING_OBJECTS", "is_not_null")));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-08",
                objectWithUniqueId("explab-2835-spl-08-uid", OBJECT,
                        param("score", "10", "INTEGER"),
                        param("amount", "10.5", "NUMBER"),
                        param("status", "OK", "STRING"),
                        param("enabled", "true", "BOOLEAN"),
                        param("date", "2026-08-05", "DATE")));

        getFlowWithRest()
                .step("Загружаем config с матрицей операторов", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что объект попал во все ожидаемые эксперименты", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertRuleExpIdsExactly(response, OBJECT, "ALL",
                            28350801L, 28350802L, 28350803L, 28350804L, 28350805L);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-09. Группа выбирается по shares, spreadValue попадает в диапазон 0..10000")
    void groupShouldBeSelectedBySharesAndSpreadValue() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                experiment(28350901,
                        SALT,
                        List.of(markerCondition(1, "SHARES")),
                        List.of(
                                group("A", Arrays.asList(share(0, 5000)), List.of(resultWithParams(1, param("actionType", "0", "INTEGER")))),
                                group("B", Arrays.asList(share(5000, 10000)), List.of(resultWithParams(1, param("actionType", "0", "INTEGER")))))));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-09",
                objectWithUniqueId("explab-2835-spl-09-uid", OBJECT, param("marker", "SHARES", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с двумя share-группами", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем выбранную группу и spreadValue", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    JsonNode exp = firstRuleExp(response, OBJECT, "MAIN");
                    assertTrue(List.of("A", "B").contains(exp.path("expGroup").asText(null)), body(response));
                    long spreadValue = exp.path("spreadValue").asLong(-1);
                    assertTrue(spreadValue >= 0 && spreadValue < 10000, body(response));
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-10. MAIN выбирает experiment с максимальным actionType priority")
    void mainShouldPreferHighestActionTypePriority() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28351001, "PRIORITY", "0"),
                markerExperiment(28351002, "PRIORITY", "3"));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-10",
                objectWithUniqueId("explab-2835-spl-10-uid", OBJECT, param("marker", "PRIORITY", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с двумя matching experiments", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем MAIN priority и полный ALL", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28351002L, "A", "A");
                    assertFirstRuleExpActionType(response, OBJECT, "MAIN", "3");
                    assertRuleExpIdsExactly(response, OBJECT, "ALL", 28351001L, 28351002L);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-11. Несколько matched conditions внутри группы сохраняют корректный conditionId")
    void multipleMatchedConditionsShouldExposeConditionId() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                experiment(28351101,
                        SALT,
                        List.of(
                                condition(1, List.of(List.of(rule("STRING", "marker1", "SPLITTING_OBJECTS", "equal", "C1")))),
                                condition(2, List.of(List.of(rule("STRING", "marker2", "SPLITTING_OBJECTS", "equal", "C2"))))),
                        List.of(group("A",
                                Arrays.asList(share(0, 10000)),
                                List.of(
                                        resultWithParams(1, param("actionType", "0", "INTEGER")),
                                        resultWithParams(2, param("actionType", "0", "INTEGER")))))));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-11",
                objectWithUniqueId("explab-2835-spl-11-uid", OBJECT,
                        param("marker1", "C1", "STRING"),
                        param("marker2", "C2", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с двумя conditions в одной группе", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем conditionId выбранного результата", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28351101L, "A", "A");
                    assertTrue(List.of(1, 2).contains(firstRuleExp(response, OBJECT, "MAIN").path("conditionId").asInt()),
                            body(response));
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-12. Layer priority выбирает итоговый MAIN среди matched layers")
    void layerPriorityShouldSelectMainExperiment() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                layeredMarkerExperiment(28351201, "LAYER", 1, 1, "0"),
                layeredMarkerExperiment(28351202, "LAYER", 2, 5, "0"));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-12",
                objectWithUniqueId("explab-2835-spl-12-uid", OBJECT, param("marker", "LAYER", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с двумя слоями", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем MAIN по layerPriority и ALL по двум слоям", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28351201L, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT, "ALL", 28351201L, 28351202L);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-13. Split использует предрасчитанные связи по uniqueConfigurationId")
    void splitShouldUsePrecalculatedLinksByUniqueConfigurationId() {
        long version = SplitterVersionProvider.next();
        String uid = "explab-2835-spl-13-" + System.nanoTime();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28351301, "PRECALC", "0"));
        SplitterPrecalcRequestDto precalc = precalcRequest(uid, "PRECALC");
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-13",
                objectWithUniqueId(uid, OBJECT, param("marker", "CHANGED_RUNTIME_VALUE", "STRING")));

        getFlowWithRest()
                .step("Загружаем config и выполняем pre-calculate", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    shouldHaveSoConfigVersion(shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(precalc)),
                            precalc.getSoConfigVersion());
                })
                .step("Проверяем, что split использует link из таблицы, а не runtime params", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28351301L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-14. Объект вне precalc table рассчитывается runtime fallback")
    void objectMissingInPrecalcTableShouldUseRuntimeFallback() {
        long version = SplitterVersionProvider.next();
        String uidInTable = "explab-2835-spl-14-table-" + System.nanoTime();
        String uidNotInTable = "explab-2835-spl-14-runtime-" + System.nanoTime();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28351401, "RUNTIME", "0"));
        SplitterPrecalcRequestDto precalc = precalcRequest(uidInTable, "NO_MATCH");
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-14",
                objectWithUniqueId(uidNotInTable, OBJECT, param("marker", "RUNTIME", "STRING")));

        getFlowWithRest()
                .step("Создаем precalc table без нужного uniqueConfigurationId", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    shouldHaveSoConfigVersion(shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(precalc)),
                            precalc.getSoConfigVersion());
                })
                .step("Проверяем runtime fallback для отсутствующего uid", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28351401L, "A", "A");
                })
                .run();
    }

    @Disabled("Требуется стендовый профиль return-suppressed=false/фильтрующие правила")
    @Test
    @DisplayName("EXPLAB-2835-SPL-15. Manual/env: suppressed object исключается из REST response")
    void suppressedObjectShouldBeExcludedWhenReturnSuppressedFalse() {
    }

    @Disabled("Требуется запуск на двух профилях emptyObjectsResponseEnabled")
    @Test
    @DisplayName("EXPLAB-2835-SPL-16. Manual/env: emptyObjectsResponseEnabled управляет пустыми объектами")
    void emptyObjectsResponseFlagShouldControlUnmatchedObjectPresence() {
    }

    @Disabled("Требуется запуск на двух профилях allRuleCodeExpEnabled")
    @Test
    @DisplayName("EXPLAB-2835-SPL-17. Manual/env: allRuleCodeExpEnabled управляет блоком ALL")
    void allRuleCodeExpFlagShouldControlAllBlockPresence() {
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-18. Kafka report содержит metadata и полный splitting result")
    void splitShouldPublishKafkaReportWithMetadata(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28351801, "REPORT", "0"));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-18",
                objectWithUniqueId("explab-2835-spl-18-uid", OBJECT, param("marker", "REPORT", "STRING")));
        long[] since = new long[1];

        getFlowWithRest()
                .step("Загружаем config", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split и фиксируем время для чтения Kafka report", flow -> {
                    since[0] = System.currentTimeMillis();
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertNoResultDtInApiResponse(response);
                })
                .step("Проверяем Kafka report по requestId", flow -> {
                    String payload = findKafkaPayloadByRequestId(kafkaService, request.getRequestId(), since[0]);
                    assertKafkaReportMeta(payload, request, version);
                    assertJsonContainsField(payload, "resultDt");
                    assertJsonContainsText(payload, "28351801");
                })
                .run();
    }

    @Disabled("Требуется fault injection недоступности explab-splitting-result или переопределение producer")
    @Test
    @DisplayName("EXPLAB-2835-SPL-19. Manual/fault: ошибка отправки Kafka report соответствует настройкам")
    void kafkaReportFailureShouldFollowIgnoreLogResultFailedSetting() {
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-20. Невалидный split request возвращает BAD_REQUEST")
    void invalidSplitRequestShouldReturnBadRequest() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28352001, "INVALID", "0"));

        getFlowWithRest()
                .step("Загружаем валидный config", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Отправляем split без splittingId", flow ->
                        flow.restCustomSteps().splitterSteps().split("{\"requestId\":\"EXPLAB-2835-SPL-20\",\"splittingObjects\":[]}")
                                .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2835-SPL-21. Большой mixed batch обрабатывает объекты независимо")
    void mixedBatchShouldProcessObjectsIndependently() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28352101, "MATCHED", "0"));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-21",
                objectWithUniqueId("explab-2835-spl-21-a", OBJECT, param("marker", "MATCHED", "STRING")),
                objectWithUniqueId("explab-2835-spl-21-b", OBJECT_2, param("marker", "NO_MATCH", "STRING")),
                objectWithUniqueId("explab-2835-spl-21-c", OBJECT_3, param("marker", "MATCHED", "STRING")));

        getFlowWithRest()
                .step("Загружаем config для mixed batch", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем независимую обработку matched/unmatched объектов", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28352101L, "A", "A");
                    assertObjectEmptyOrAbsent(response, OBJECT_2);
                    assertFirstRuleExp(response, OBJECT_3, "MAIN", 28352101L, "A", "A");
                    assertSplittingResultsHaveUniqueObjectIds(response);
                })
                .run();
    }

    @Disabled("На dev ручная загрузка REACTIONS config выключена: endpoint возвращает LOAD_ERROR/Manual config load disabled")
    @Test
    @DisplayName("EXPLAB-2835-SPL-22. REACTIONS profile не возвращает isAlternative=true")
    void reactionsProfileShouldNotExposeAlternativeMain() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.REACTIONS,
                version,
                markerExperiment(28352201, "REACTIONS", "3"));
        SplitRequestDto request = splitRequest("EXPLAB-2835-SPL-22",
                objectWithUniqueId("explab-2835-spl-22-uid", OBJECT, param("marker", "REACTIONS", "STRING")));

        getFlowWithRest()
                .step("Загружаем REACTIONS config", flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Выполняем reactions split и проверяем отсутствие alternative=true", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.REACTIONS, request);
                    assertBasicResponseContract(response, request, version);
                    assertNoAlternativeTrueAnywhere(response);
                    assertRuleExpsUseWorkedGroups(response, OBJECT, "MAIN");
                })
                .run();
    }

    private ExperimentDto markerExperiment(int id, String marker, String actionType) {
        return experiment(id,
                SALT,
                List.of(markerCondition(1, marker)),
                List.of(actionGroup("A", 1, actionType)));
    }

    private ExperimentDto layeredMarkerExperiment(int id, String marker, int layerId, int layerPriority, String actionType) {
        return ExperimentDto.builder()
                .id(id)
                .purpose("DCG")
                .salt(SALT + "-" + id)
                .layerId(layerId)
                .layerPriority(layerPriority)
                .objectSelectConditions(List.of(markerCondition(1, marker)))
                .groups(List.of(actionGroup("A", 1, actionType)))
                .build();
    }

    private ExperimentDto operatorExperiment(int id, RuleDto rule) {
        return experiment(id,
                SALT + "-" + id,
                List.of(condition(1, List.of(List.of(rule)))),
                List.of(actionGroup("A", 1, "0")));
    }

    private ObjectSelectConditionDto markerCondition(int id, String marker) {
        return condition(id, List.of(List.of(rule("STRING", "marker", "SPLITTING_OBJECTS", "equal", marker))));
    }

    private GroupDto actionGroup(String code, int conditionId, String actionType) {
        return group(code,
                Arrays.asList(share(0, 10000)),
                List.of(resultWithParams(conditionId, param("actionType", actionType, "INTEGER"))));
    }

    private SplitterPrecalcRequestDto precalcRequest(String uniqueConfigurationId, String marker) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(SO_VERSION.incrementAndGet())
                .splittingObjects(List.of(SplitterPrecalcObjectDto.builder()
                        .uniqueConfigurationId(uniqueConfigurationId)
                        .objectParams(List.of(new SplitterPrecalcParamDto("marker", List.of(marker), "STRING")))
                        .build()))
                .build();
    }
}

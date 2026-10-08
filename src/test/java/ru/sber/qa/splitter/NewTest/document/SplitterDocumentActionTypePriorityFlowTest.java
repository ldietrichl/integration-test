package ru.sber.qa.splitter.NewTest.document;

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
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@AnyConfigLoadMode
public class SplitterDocumentActionTypePriorityFlowTest extends AbstractSplitterDocumentFlowTest {

    private static final String SCENARIO_SECTION = "1. Тест на приоритет Целевого действия";
    private static final String PRIORITY_SALT = "24096d2M1e";
    private static final String PRIORITY_SPLITTING_ID = "100";
    private static final String CHANNEL_PARAM = "channelId";
    private static final String CHANNEL_VALUE = "20";
    private static final String PRODUCT_PARAM = "productId";
    private static final String PRODUCT_VALUE = "1-AVDHSSS";

    /**
     * ConfigMap-dependent:
     * final-exp-rule.values-map на текущем стенде задает порядок приоритета actionType:
     * 2 > 4 > 3 > 6 > 5 > 1 > 0.
     * filter-rule.values=[2,4], поэтому filtered=true ожидается только для MAIN с actionType 2/4.
     * Отрицательные actionType из таблицы документа в текущей ConfigMap не заведены, поэтому здесь
     * проверяется исполнимая часть таблицы 0/1/5/6/3/4/2 и дополнительные tie-break проверки.
     */
    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-01. полный конфиг из таблицы: MAIN выбирается с actionType=2")
    void fullDocumentConfigShouldChooseActionType2() {
        runPriorityScenario(
                "полный конфиг из таблицы: actionType 0/1/2/3/4/5/6, по два experiment на значение",
                tableExperiments(),
                6,
                "2");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-02. без actionType=2: MAIN выбирается с actionType=4")
    void configWithoutActionType2ShouldChooseActionType4() {
        runPriorityScenario(
                "из конфига исключены experiment с actionType=2; максимальный доступный priority у actionType=4",
                experimentsByIds(2, 3, 4, 5, 8, 9, 10, 11, 12, 13, 14, 15),
                10,
                "4");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-03. без actionType=2/4: MAIN выбирается с actionType=3")
    void configWithoutActionType2And4ShouldChooseActionType3() {
        runPriorityScenario(
                "из конфига исключены actionType=2 и actionType=4; максимальный доступный priority у actionType=3",
                experimentsByIds(2, 3, 4, 5, 8, 9, 12, 13, 14, 15),
                8,
                "3");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-04. без actionType=2/4/3: MAIN выбирается с actionType=6")
    void configWithoutActionType2And4And3ShouldChooseActionType6() {
        runPriorityScenario(
                "из конфига исключены actionType=2/4/3; максимальный доступный priority у actionType=6",
                experimentsByIds(2, 3, 4, 5, 12, 13, 14, 15),
                14,
                "6");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-05. без actionType=2/4/3/6: MAIN выбирается с actionType=5")
    void configWithoutActionType2And4And3And6ShouldChooseActionType5() {
        runPriorityScenario(
                "из конфига исключены actionType=2/4/3/6; максимальный доступный priority у actionType=5",
                experimentsByIds(2, 3, 4, 5, 12, 13),
                12,
                "5");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-06. без actionType=2/4/3/6/5: MAIN выбирается с actionType=1")
    void configWithoutActionType2And4And3And6And5ShouldChooseActionType1() {
        runPriorityScenario(
                "из конфига исключены actionType=2/4/3/6/5; максимальный доступный priority у actionType=1",
                experimentsByIds(2, 3, 4, 5),
                4,
                "1");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-07. только actionType=0: MAIN выбирается с actionType=0")
    void configWithOnlyActionType0ShouldChooseActionType0() {
        runPriorityScenario(
                "в конфиге остались только experiment с actionType=0",
                experimentsByIds(2, 3),
                2,
                "0");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-08. одинаковый actionType=2: tie-break по минимальному expId")
    void sameActionType2ShouldChooseMinimalExpId() {
        runPriorityScenario(
                "в конфиге два experiment с actionType=2 в обратном порядке; при равном priority побеждает меньший expId",
                experimentsByIds(7, 6),
                6,
                "2");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-09. одинаковый actionType=4: tie-break по минимальному expId")
    void sameActionType4ShouldChooseMinimalExpId() {
        runPriorityScenario(
                "в конфиге два experiment с actionType=4 в обратном порядке; при равном priority побеждает меньший expId",
                experimentsByIds(11, 10),
                10,
                "4");
    }

    @CriticalRegression
    @Test
    @DisplayName("AT-PRIORITY-10. порядок экспериментов в конфиге не влияет на выбор MAIN")
    void reversedDocumentConfigShouldStillChooseActionType2() {
        List<ExperimentDto> reversed = new ArrayList<>(tableExperiments());
        Collections.reverse(reversed);
        runPriorityScenario(
                "полный конфиг из таблицы загружен в обратном порядке; MAIN все равно выбирается по priority actionType",
                reversed,
                6,
                "2");
    }

    private void runPriorityScenario(String configDescription,
                                     List<ExperimentDto> experiments,
                                     int expectedMainExpId,
                                     String expectedMainActionType) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiments.toArray(new ExperimentDto[0]));
        SplitRequestDto request = splitRequest(PRIORITY_SPLITTING_ID,
                object(MATCHING_OBJECT_ID,
                        param(CHANNEL_PARAM, CHANNEL_VALUE, "INTEGER"),
                        param(PRODUCT_PARAM, PRODUCT_VALUE, "STRING")));
        Long[] expectedAllExpIds = experiments.stream()
                .map(experiment -> experiment.getId().longValue())
                .toArray(Long[]::new);
        int expectedSpreadValue = spread(PRIORITY_SALT, PRIORITY_SPLITTING_ID);
        AtomicReference<ValidatableResponseWrapper> splitResponse = new AtomicReference<>();

        getFlowWithRest()
                .step("Загружаем config для раздела '" + SCENARIO_SECTION + "': " + configDescription,
                        flow -> loadConfigStep(flow, config))
                .step("Отправляем REST split request: POST /api/v1/splitter/mapper/split; "
                                + "splittingId=" + PRIORITY_SPLITTING_ID
                                + ", objectId=" + MATCHING_OBJECT_ID
                                + ", objectParams=[channelId=20, productId=1-AVDHSSS]",
                        flow -> splitResponse.set(shouldBe200(split(flow, request))))
                .step("Проверяем базовую структуру response: version, объект, MAIN и ALL", flow -> {
                    ValidatableResponseWrapper response = requireSplitResponse(splitResponse);

                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 1);
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertRuleResultSize(response, MATCHING_OBJECT_ID, "MAIN", 1);
                    assertRuleResultSize(response, MATCHING_OBJECT_ID, "ALL", expectedAllExpIds.length);
                })
                .step("Проверяем MAIN: выбранный experiment соответствует приоритету actionType", flow -> {
                    ValidatableResponseWrapper response = requireSplitResponse(splitResponse);

                    assertMainExp(response, MATCHING_OBJECT_ID, expectedMainExpId);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainActionType(response, MATCHING_OBJECT_ID, expectedMainActionType);
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, expectedSpreadValue);
                    assertFiltered(response, MATCHING_OBJECT_ID, expectedFilteredValue(expectedMainActionType));
                })
                .step("Проверяем ALL: все связанные эксперименты присутствуют, winner отмечен корректно", flow -> {
                    ValidatableResponseWrapper response = requireSplitResponse(splitResponse);

                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, expectedAllExpIds);
                    assertAllExpGroup(response, MATCHING_OBJECT_ID, expectedMainExpId, "A");
                    assertAllExpActionType(response, MATCHING_OBJECT_ID, expectedMainExpId, expectedMainActionType);
                })
                .run();
    }

    private ValidatableResponseWrapper requireSplitResponse(AtomicReference<ValidatableResponseWrapper> splitResponse) {
        ValidatableResponseWrapper response = splitResponse.get();
        if (response == null) {
            throw new AssertionError("Split response отсутствует: предыдущий flow step не выполнил split request");
        }
        return response;
    }

    private List<ExperimentDto> tableExperiments() {
        return experimentsByIds(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    private List<ExperimentDto> experimentsByIds(int... ids) {
        Map<Integer, String> actionTypes = actionTypesByExpId();
        List<ExperimentDto> result = new ArrayList<>();
        for (int id : ids) {
            result.add(priorityExperiment(id, actionTypes.get(id)));
        }
        return result;
    }

    private Map<Integer, String> actionTypesByExpId() {
        Map<Integer, String> result = new LinkedHashMap<>();
        result.put(2, "0");
        result.put(3, "0");
        result.put(4, "1");
        result.put(5, "1");
        result.put(6, "2");
        result.put(7, "2");
        result.put(8, "3");
        result.put(9, "3");
        result.put(10, "4");
        result.put(11, "4");
        result.put(12, "5");
        result.put(13, "5");
        result.put(14, "6");
        result.put(15, "6");
        return result;
    }

    private ExperimentDto priorityExperiment(int id, String actionType) {
        return ExperimentDto.builder()
                .id(id)
                .purpose("DCG")
                .layerId(1)
                .salt(PRIORITY_SALT)
                .objectSelectConditions(List.of(condition(1, List.of(List.of(
                        rule("INTEGER", CHANNEL_PARAM, "SPLITTING_OBJECTS", "in", CHANNEL_VALUE),
                         rule("STRING", PRODUCT_PARAM, "SPLITTING_OBJECTS", "in", PRODUCT_VALUE))))))
                .groups(List.of(group("A", 0, shareTo(id), 1, actionType)))
                .build();
    }

    private int shareTo(int expId) {
        // В таблице раздела первый experiment c actionType=0 занимает полный диапазон [0;10000),
        // остальные experiment проверяются на диапазоне [0;7500). Для splittingId=100 spreadValue=3295,
        // поэтому объект попадает во все experiment из конфигурации.
        return expId == 2 ? 10000 : 7500;
    }

    private String expectedFilteredValue(String actionType) {
        return List.of("2", "4").contains(actionType) ? "true" : "false";
    }
}

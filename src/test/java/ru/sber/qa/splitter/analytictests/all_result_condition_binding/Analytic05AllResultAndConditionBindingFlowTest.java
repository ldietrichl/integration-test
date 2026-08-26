package ru.sber.qa.splitter.analytictests.all_result_condition_binding;


import ru.sber.qa.allure.ManualTest;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
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
import util.support.SplitterVersionProvider;

import java.util.List;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
/**
 * REST-only analytic splitter coverage class.
 *
 * Required ConfigMap contract: src/test/resources/splitter/configmap/mapper-current.yml.
 * The test does not apply ConfigMap automatically; the target environment must be configured with compatible rules.
 */
public class Analytic05AllResultAndConditionBindingFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: К объекту привязалось много экспериментов. Проверяем, что все они присутствуют в ALL.")
    @DisplayName("AN-ALL-01. ALL содержит все эксперименты, связанные с одним объектом")
    void allShouldContainEveryExperimentLinkedToObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7501, "AN-ALL-01-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7502, "AN-ALL-01-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "1"))),
                experiment(7503, "AN-ALL-01-C", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "5"))));
        SplitRequestDto split = splitRequest("AN-ALL-01", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем три эксперимента, связанные с одним объектом", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что ALL содержит все связанные expId", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 7501L, 7502L, 7503L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: У эксперимента группы привязались к разным объектам; проверяем, что ALL не смешивает чужие связи.")
    @DisplayName("AN-ALL-02. ALL по нескольким объектам не смешивает чужие objectSelectConditions")
    void allShouldNotMixExperimentsBetweenObjects() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(7504, "AN-ALL-02-A", List.of(objectParamEqualsCondition(1, "segment", "A", "STRING")), List.of(fullRangeGroup("A", 1, "0"))),
                experiment(7505, "AN-ALL-02-B", List.of(objectParamEqualsCondition(1, "segment", "B", "STRING")), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto split = splitRequest("AN-ALL-02",
                object(MATCHING_OBJECT_ID, param("segment", "A", "STRING")),
                object(SECOND_OBJECT_ID, param("segment", "B", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с разными conditions для двух объектов", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что каждый объект получил только свои linked experiments", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 7504L);
                    assertAllRuleHasExpIdsExactly(response, SECOND_OBJECT_ID, 7505L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: В эксперименте много condition, в группе один condition; result должен быть связан с condition, по которому привязался объект.")
    @DisplayName("AN-ALL-03. При нескольких condition в группе используется condition, по которому привязался объект")
    void conditionBindingShouldKeepMatchedConditionForResult() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto c1 = objectParamEqualsCondition(1, "id", "1", "INTEGER");
        ObjectSelectConditionDto c2 = objectParamEqualsCondition(2, "id", "2", "INTEGER");
        LoadConfigRequestDto config = config(version,
                experiment(7506, "AN-ALL-03", List.of(c1, c2), List.of(
                        group("A", List.of(share(0, 10000)), List.of(
                                result(1, "0"),
                                result(2, "1"))))));
        SplitRequestDto split = splitRequest("AN-ALL-03", object(MATCHING_OBJECT_ID, param("id", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с двумя condition и двумя group results", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что result привязан к condition=2", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertAllExpActionType(response, MATCHING_OBJECT_ID, 7506L, "1");
                })
                .run();
    }


    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: В эксперименте много condition, в группе много condition; выбирается корректный conditionId и связанный result.")
    @DisplayName("AN-ALL-04. Много condition в experiment и много condition в group: выбирается корректный conditionId")
    void manyConditionsInExperimentAndGroupShouldKeepMatchedConditionId() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto c1 = objectParamEqualsCondition(1, "segment", "A", "STRING");
        ObjectSelectConditionDto c2 = objectParamEqualsCondition(2, "segment", "B", "STRING");
        ObjectSelectConditionDto c3 = objectParamEqualsCondition(3, "segment", "C", "STRING");
        LoadConfigRequestDto config = config(version,
                experiment(7507, "AN-ALL-04", List.of(c1, c2, c3), List.of(
                        group("A", List.of(share(0, 10000)), List.of(
                                result(1, "0"),
                                result(2, "1"),
                                result(3, "5"))))));
        SplitRequestDto split = splitRequest("AN-ALL-04", object(MATCHING_OBJECT_ID, param("segment", "B", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с тремя conditions и тремя group result bindings", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что сработал result для conditionId=2", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertAllExpConditionId(response, MATCHING_OBJECT_ID, 7507L, 2);
                    assertAllExpActionType(response, MATCHING_OBJECT_ID, 7507L, "1");
                    assertMainConditionId(response, MATCHING_OBJECT_ID, 2);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "1");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Эксперимент привязан к объекту не сработавшей группой с высоким actionType; несработавшая группа не должна влиять на MAIN.")
    @DisplayName("AN-ALL-05. Несработавшая группа с высоким actionType не попадает в MAIN и не влияет на выбор")
    void nonMatchedGroupWithHigherActionTypeShouldNotAffectMainOrAllResultParams() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto matched = objectParamEqualsCondition(1, "id", "1", "INTEGER");
        ObjectSelectConditionDto notMatched = objectParamEqualsCondition(2, "id", "2", "INTEGER");
        LoadConfigRequestDto config = config(version,
                experiment(7508, "AN-ALL-05", List.of(matched, notMatched), List.of(
                        group("A", List.of(share(0, 10000)), List.of(
                                result(1, "1"),
                                result(2, "6"))))));
        SplitRequestDto split = splitRequest("AN-ALL-05", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config, где несработавший condition имеет более высокий actionType", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что MAIN выбран по matched condition=1", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertMainExp(response, MATCHING_OBJECT_ID, 7508L);
                    assertMainConditionId(response, MATCHING_OBJECT_ID, 1);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "1");
                    assertAllExpConditionId(response, MATCHING_OBJECT_ID, 7508L, 1);
                    assertAllExpActionType(response, MATCHING_OBJECT_ID, 7508L, "1");
                })
                .run();
    }


    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Группа привязывается к объекту по двум и трем condition; результат должен быть один и связан с минимальным matched conditionId.")
    @DisplayName("AN-ALL-07. Одна группа с несколькими matched conditions выбирает минимальный conditionId")
    void oneGroupWithSeveralMatchedConditionsShouldUseSmallestConditionId() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto c1 = objectParamEqualsCondition(1, "id", "1", "INTEGER");
        ObjectSelectConditionDto c2 = objectParamEqualsCondition(2, "segment", "A", "STRING");
        ObjectSelectConditionDto c3 = objectParamEqualsCondition(3, "channel", "WEB", "STRING");
        LoadConfigRequestDto config = config(version,
                experiment(7509, "AN-ALL-07", List.of(c1, c2, c3), List.of(
                        group("A", List.of(share(0, 10000)), List.of(
                                result(1, "0"),
                                result(2, "1"),
                                result(3, "5"))))));
        SplitRequestDto split = splitRequest("AN-ALL-07", object(MATCHING_OBJECT_ID,
                param("id", "1", "INTEGER"),
                param("segment", "A", "STRING"),
                param("channel", "WEB", "STRING")));

        getFlowWithRest()
                .step("Загружаем config, где одна группа содержит три matched condition", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что выбран минимальный matched conditionId=1", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertAllExpConditionId(response, MATCHING_OBJECT_ID, 7509L, 1);
                    assertAllExpActionType(response, MATCHING_OBJECT_ID, 7509L, "0");
                    assertMainConditionId(response, MATCHING_OBJECT_ID, 1);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Две группы эксперимента привязываются к объекту по нескольким condition; выбранная по spreadValue группа должна использовать свой минимальный matched conditionId.")
    @DisplayName("AN-ALL-08. Несколько групп с несколькими matched conditions выбирают conditionId внутри выбранной группы")
    void severalGroupsWithSeveralMatchedConditionsShouldUseSmallestConditionIdInSelectedGroup() {
        long version = SplitterVersionProvider.next();
        String salt = "AN-ALL-08-SALT";
        String splitForA = splittingIdForExactSpread("AN-ALL-08-A-SP1000", salt, 1000);
        String splitForB = splittingIdForExactSpread("AN-ALL-08-B-SP7000", salt, 7000);
        ObjectSelectConditionDto c1 = objectParamEqualsCondition(1, "id", "1", "INTEGER");
        ObjectSelectConditionDto c2 = objectParamEqualsCondition(2, "segment", "A", "STRING");
        ObjectSelectConditionDto c3 = objectParamEqualsCondition(3, "channel", "WEB", "STRING");
        ObjectSelectConditionDto c4 = objectParamEqualsCondition(4, "template", "T1", "STRING");
        LoadConfigRequestDto config = config(version,
                experiment(7510, salt, List.of(c1, c2, c3, c4), List.of(
                        // В AN-ALL-08 проверяем только condition binding внутри выбранной по spreadValue группы.
                        // Поэтому не используем actionType=5/6: они входят в alt-control текущей ConfigMap
                        // и уводят сценарий в alternative/MAIN logic, что проверяется отдельными ALT-сценариями.
                        group("A", List.of(share(0, 5000)), List.of(result(1, "0"), result(2, "1"))),
                        group("B", List.of(share(5000, 10000)), List.of(result(3, "0"), result(4, "1"))))));
        var matchingObject = object(MATCHING_OBJECT_ID,
                param("id", "1", "INTEGER"),
                param("segment", "A", "STRING"),
                param("channel", "WEB", "STRING"),
                param("template", "T1", "STRING"));

        getFlowWithRest()
                .step("Загружаем config с двумя группами, каждая содержит несколько matched condition", flow -> loadConfigStep(flow, config))
                .step("При spreadValue=1000 выбирается группа A и минимальный conditionId=1", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitForA, matchingObject)));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 1000);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainConditionId(response, MATCHING_OBJECT_ID, 1);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                    assertFiltered(response, MATCHING_OBJECT_ID, "false");
                    assertAllExpActionType(response, MATCHING_OBJECT_ID, 7510L, 1, "A", "0");
                    assertAllExpAlternativeFlag(response, MATCHING_OBJECT_ID, 7510L, 1, "A", "false");
                    assertAllDoesNotContainBinding(response, MATCHING_OBJECT_ID, 7510L, 3, "B");
                })
                .step("При spreadValue=7000 выбирается группа B и минимальный conditionId=3 без ухода в alternative", flow -> {
                    var response = shouldBe200(split(flow, splitRequest(splitForB, matchingObject)));
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, 7000);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "B");
                    assertMainConditionId(response, MATCHING_OBJECT_ID, 3);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                    assertFiltered(response, MATCHING_OBJECT_ID, "false");
                    assertAllExpActionType(response, MATCHING_OBJECT_ID, 7510L, 3, "B", "0");
                    assertAllDoesNotContainBinding(response, MATCHING_OBJECT_ID, 7510L, 1, "A");
                    assertAllExpAlternativeFlag(response, MATCHING_OBJECT_ID, 7510L, 3, "B", "false");
                })
                .run();
    }

    @Disabled("Нужна управляемая настройка ConfigMap: опция отключения ALL не доступна из текущего REST flow")
    @ManualTest
    @Test
    @AnalyticTag("Аналитика: Опция \"Отдавать ALL\" выключена. ALL не должен возвращаться.")
    @DisplayName("AN-ALL-06. При выключенной опции отдавать ALL результат ALL не возвращается")
    void allRuleShouldDisappearWhenAllOutputDisabled() {
    }
}

package ru.sber.qa.splitter.NewTest.document;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
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

import java.util.List;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterDocument01LogicalDescriptionFlowTest extends AbstractSplitterDocumentFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DOC-01-01. rules: одна AND-группа с одним выражением выбирает только matching object")
    void oneGroupOneExpressionShouldBindOnlyMatchingObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(
                10101,
                "DOC-01-01-SALT",
                List.of(objectParamInCondition(1, "commId", "INTEGER", "250524")),
                List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("commId", "250524", "INTEGER")),
                object(NEGATIVE_OBJECT_ID, param("commId", "250525", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с одним condition и одним expression", flow -> loadConfigStep(flow, config))
                .step("Выполняем split для matching и non-matching объектов", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10101L);
                    assertObjectResultsEmpty(response, NEGATIVE_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-01-02. rules: одна AND-группа с тремя выражениями требует выполнения всех трех")
    void oneGroupThreeExpressionsShouldRequireAllExpressions() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto condition = condition(1, List.of(List.of(
                rule("INTEGER", "commId", "SPLITTING_OBJECTS", "in", "250524"),
                rule("INTEGER", "channelId", "SPLITTING_OBJECTS", "in", "20"),
                rule("INTEGER", "modelId", "SPLITTING_OBJECTS", "in", "8")
        )));
        LoadConfigRequestDto config = config(version, experiment(
                10102,
                "DOC-01-02-SALT",
                List.of(condition),
                List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID,
                        param("commId", "250524", "INTEGER"),
                        param("channelId", "20", "INTEGER"),
                        param("modelId", "8", "INTEGER")),
                object(NEGATIVE_OBJECT_ID,
                        param("commId", "250524", "INTEGER"),
                        param("channelId", "20", "INTEGER"),
                        param("modelId", "9", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с одной AND-группой из трех выражений", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что неполное совпадение не привязывает эксперимент", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10102L);
                    assertObjectResultsEmpty(response, NEGATIVE_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-01-03. rules: три OR-группы по одному выражению выбирают три разных объекта")
    void threeOrGroupsShouldBindObjectsByAnyGroup() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto condition = condition(1, List.of(
                List.of(rule("STRING", "segment", "SPLITTING_OBJECTS", "equal", "A")),
                List.of(rule("STRING", "segment", "SPLITTING_OBJECTS", "equal", "B")),
                List.of(rule("STRING", "segment", "SPLITTING_OBJECTS", "equal", "C"))
        ));
        LoadConfigRequestDto config = config(version, experiment(
                10103,
                "DOC-01-03-SALT",
                List.of(condition),
                List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("segment", "A", "STRING")),
                object(SECOND_OBJECT_ID, param("segment", "B", "STRING")),
                object(THIRD_OBJECT_ID, param("segment", "C", "STRING")),
                object(NEGATIVE_OBJECT_ID, param("segment", "D", "STRING")));

        getFlowWithRest()
                .step("Загружаем конфиг с тремя OR-группами", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что каждая OR-группа независимо привязывает объект", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10103L);
                    assertMainExp(response, SECOND_OBJECT_ID, 10103L);
                    assertMainExp(response, THIRD_OBJECT_ID, 10103L);
                    assertObjectResultsEmpty(response, NEGATIVE_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-01-04. rules: смешанная OR-структура 1 expression OR 3 expressions")
    void mixedOrGroupsShouldUseOrBetweenGroupsAndAndInsideGroup() {
        long version = SplitterVersionProvider.next();
        ObjectSelectConditionDto condition = condition(1, List.of(
                List.of(rule("STRING", "segment", "SPLITTING_OBJECTS", "equal", "VIP")),
                List.of(
                        rule("INTEGER", "commId", "SPLITTING_OBJECTS", "equal", "250524"),
                        rule("INTEGER", "channelId", "SPLITTING_OBJECTS", "equal", "20"),
                        rule("INTEGER", "modelId", "SPLITTING_OBJECTS", "equal", "8"))
        ));
        LoadConfigRequestDto config = config(version, experiment(
                10104,
                "DOC-01-04-SALT",
                List.of(condition),
                List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("segment", "VIP", "STRING")),
                object(SECOND_OBJECT_ID,
                        param("commId", "250524", "INTEGER"),
                        param("channelId", "20", "INTEGER"),
                        param("modelId", "8", "INTEGER")),
                object(NEGATIVE_OBJECT_ID,
                        param("commId", "250524", "INTEGER"),
                        param("channelId", "20", "INTEGER"),
                        param("modelId", "9", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с OR-группами разной размерности", flow -> loadConfigStep(flow, config))
                .step("Проверяем OR между группами и AND внутри группы", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10104L);
                    assertMainExp(response, SECOND_OBJECT_ID, 10104L);
                    assertObjectResultsEmpty(response, NEGATIVE_OBJECT_ID);
                })
                .run();
    }
}

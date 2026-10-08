package ru.sber.qa.scheduler.regression;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.special.EnvironmentConfigWithScheduler;
import constants.Endpoints.Scheduler;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.scheduler.SchedulerTestDataFactory;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.scheduler.AbstractSchedulerFlowTest;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.scheduler.SchedulerAssertions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static request.scheduler.SchedulerTestDataFactory.filter;
import static request.scheduler.SchedulerTestDataFactory.firstAction;
import static steps.rest.scheduler.SchedulerSteps.expect;
import static util.scheduler.SchedulerAssertions.*;

@ExtendWith({PerfeccionistaExtension.class, config.extensions.scheduler.SchedulerExecutionCondition.class})
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithScheduler.class)
@ResourceLock("scheduler-service-regression")
@Epic("Scheduler service")
@Feature("Scheduler regression: documentation and code risks")
@config.extensions.scheduler.UsesScenarioSteps(steps.flow.scheduler.SchedulerApiRegressionSteps.class)
public class SchedulerApiRegressionFlowTest extends AbstractSchedulerFlowTest {

    @Regression
    @CriticalRegression
    @Test
    @DisplayName("SCH-002. Новая задача недоступна через legacy GET")
    void sch002() {
        getFlowWithRest().step("SCH-002. Новая задача недоступна через legacy GET",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch002()).run();
    }






    @Regression
    @Test
    @DisplayName("SCH-008. Пагинация: 23 записи; page=0,size=10")
    void sch008() {
        getFlowWithRest().step("SCH-008. Пагинация: 23 записи; page=0,size=10",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch008()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-009. Пагинация: 23 записи; page=2,size=10")
    void sch009() {
        getFlowWithRest().step("SCH-009. Пагинация: 23 записи; page=2,size=10",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch009()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-010. Пагинация: 23 записи; page=3,size=10")
    void sch010() {
        getFlowWithRest().step("SCH-010. Пагинация: 23 записи; page=3,size=10",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch010()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-011. Пагинация: 0 записей; page=0,size=10")
    void sch011() {
        getFlowWithRest().step("SCH-011. Пагинация: 0 записей; page=0,size=10",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch011()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-012. AND внутри группы, OR между группами")
    void sch012() {
        getFlowWithRest().step("SCH-012. AND внутри группы, OR между группами",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch012()).run();
    }

    @Regression
    @CriticalRegression
    @Test
    @DisplayName("SCH-013. MY_TASKS: только подтверждённый авторизованный пользователь")
    // Temporary prerequisite block. Service-defect scenarios remain enabled.
    @org.junit.jupiter.api.Disabled("BLOCKED_TEST_DATA SCH-013: authenticated application principal is not confirmed. Re-enable after approved token/sub-to-user mapping and caller contract are established; anonymous create author is not identity.")
    void sch013() {
        getFlowWithRest().step("SCH-013. MY_TASKS: только подтверждённый авторизованный пользователь",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch013()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-016. Противоречие preset и фильтра")
    void sch016() {
        getFlowWithRest().step("SCH-016. Противоречие preset и фильтра",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch016()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-018. Поиск не игнорируется")
    // Temporary prerequisite block. Service-defect scenarios remain enabled.
    @org.junit.jupiter.api.Disabled("BLOCKED_TEST_DATA SCH-018: objectName search contract is unconfirmed. Re-enable after specification/analyst confirmation and a controlled search witness; not because a service assertion failed.")
    void sch018() {
        getFlowWithRest().step("SCH-018. Поиск не игнорируется",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch018()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-019. Сортировка ASC")
    void sch019() {
        getFlowWithRest().step("SCH-019. Сортировка ASC",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch019()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-020. Сортировка DESC")
    void sch020() {
        getFlowWithRest().step("SCH-020. Сортировка DESC",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch020()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-021. Сортировка по нескольким полям")
    void sch021() {
        getFlowWithRest().step("SCH-021. Сортировка по нескольким полям",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch021()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-022. Невалидная пагинация: page отсутствует")
    void sch022() {
        getFlowWithRest().step("SCH-022. Невалидная пагинация: page отсутствует",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch022()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-023. Невалидная пагинация: size отсутствует")
    void sch023() {
        getFlowWithRest().step("SCH-023. Невалидная пагинация: size отсутствует",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch023()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-024. Невалидная пагинация: page=-1")
    void sch024() {
        getFlowWithRest().step("SCH-024. Невалидная пагинация: page=-1",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch024()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-025. Невалидная пагинация: size=0")
    void sch025() {
        getFlowWithRest().step("SCH-025. Невалидная пагинация: size=0",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch025()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-026. Невалидная пагинация: size=-1")
    void sch026() {
        getFlowWithRest().step("SCH-026. Невалидная пагинация: size=-1",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch026()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-027. Невалидная пагинация: page=\"abc\"")
    void sch027() {
        getFlowWithRest().step("SCH-027. Невалидная пагинация: page=\\",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch027()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-029. Данные инициатора")
    void sch029() {
        getFlowWithRest().step("SCH-029. Данные инициатора",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch029()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-030. Название точки сплиттования")
    void sch030() {
        getFlowWithRest().step("SCH-030. Название точки сплиттования",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch030()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-031. Представление статуса PLANNED")
    void sch031() {
        getFlowWithRest().step("SCH-031. Представление статуса PLANNED",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch031()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-032. Представление статуса IN_PROGRESS")
    void sch032() {
        getFlowWithRest().step("SCH-032. Представление статуса IN_PROGRESS",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch032()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-033. Представление статуса COMPLETED")
    void sch033() {
        getFlowWithRest().step("SCH-033. Представление статуса COMPLETED",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch033()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-034. Представление статуса ERROR")
    void sch034() {
        getFlowWithRest().step("SCH-034. Представление статуса ERROR",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch034()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-035. Представление статуса NOT_STARTED")
    void sch035() {
        getFlowWithRest().step("SCH-035. Представление статуса NOT_STARTED",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch035()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-036. Обязательное поле splittingPointCode")
    void sch036() {
        getFlowWithRest().step("SCH-036. Обязательное поле splittingPointCode",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch036()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-037. Обязательное поле scheduleDateTime")
    void sch037() {
        getFlowWithRest().step("SCH-037. Обязательное поле scheduleDateTime",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch037()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-038. Обязательное поле createdBy")
    void sch038() {
        getFlowWithRest().step("SCH-038. Обязательное поле createdBy",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch038()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-039. Обязательное поле actions")
    void sch039() {
        getFlowWithRest().step("SCH-039. Обязательное поле actions",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch039()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-040. Обязательное поле actions[0].objectType")
    void sch040() {
        getFlowWithRest().step("SCH-040. Обязательное поле actions[0].objectType",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch040()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-041. Обязательное поле actions[0].objectId")
    void sch041() {
        getFlowWithRest().step("SCH-041. Обязательное поле actions[0].objectId",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch041()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-042. Обязательное поле actions[0].objectName")
    void sch042() {
        getFlowWithRest().step("SCH-042. Обязательное поле actions[0].objectName",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch042()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-043. Обязательное поле actions[0].action")
    void sch043() {
        getFlowWithRest().step("SCH-043. Обязательное поле actions[0].action",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch043()).run();
    }

    @Regression
    @CriticalRegression
    @Test
    @DisplayName("SCH-044. Пустой actions")
    void sch044() {
        getFlowWithRest().step("SCH-044. Пустой actions",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch044()).run();
    }

    @Regression
    @CriticalRegression
    @Test
    @DisplayName("SCH-045. Статус и версия новой задачи")
    void sch045() {
        getFlowWithRest().step("SCH-045. Статус и версия новой задачи",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch045()).run();
    }

    @Regression
    @CriticalRegression
    @Test
    @DisplayName("SCH-046. Два действия одной задачи")
    void sch046() {
        getFlowWithRest().step("SCH-046. Два действия одной задачи",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch046()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-048. Контракт ответа создания")
    void sch048() {
        getFlowWithRest().step("SCH-048. Контракт ответа создания",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch048()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-049. Создание для типа EXP")
    void sch049() {
        getFlowWithRest().step("SCH-049. Создание для типа EXP",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch049()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-050. Создание для типа CJ")
    void sch050() {
        getFlowWithRest().step("SCH-050. Создание для типа CJ",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch050()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-051. Создание для типа SPLIT")
    void sch051() {
        getFlowWithRest().step("SCH-051. Создание для типа SPLIT",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch051()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-052. Создание для типа PILOT")
    void sch052() {
        getFlowWithRest().step("SCH-052. Создание для типа PILOT",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch052()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-053. Неизвестное действие")
    void sch053() {
        getFlowWithRest().step("SCH-053. Неизвестное действие",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch053()).run();
    }

    @Regression
    @CriticalRegression
    @Test
    @DisplayName("SCH-054. Удаление по объекту и статусу")
    void sch054() {
        getFlowWithRest().step("SCH-054. Удаление по объекту и статусу",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch054()).run();
    }

    @Regression
    @CriticalRegression
    @Test
    @DisplayName("SCH-055. Удаление более 2000 задач")
    void sch055() {
        getFlowWithRest().step("SCH-055. Удаление более 2000 задач",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch055()).run();
    }

    @Regression
    @CriticalRegression
    @Test
    @DisplayName("SCH-056. Первые 2000 действий относятся к завершённым задачам")
    void sch056() {
        getFlowWithRest().step("SCH-056. Первые 2000 действий относятся к завершённым задачам",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch056()).run();
    }


    @Regression
    @Test
    @DisplayName("SCH-093. Ошибка валидации с UUID")
    void sch093() {
        getFlowWithRest().step("SCH-093. Ошибка валидации с UUID",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch093()).run();
    }








    @Regression
    @Test
    @DisplayName("SCH-126. Негативный фильтр: неизвестный paramCode")
    void sch126() {
        getFlowWithRest().step("SCH-126. Негативный фильтр: неизвестный paramCode",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch126()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-127. Негативный фильтр: неразрешённый operatorCode")
    void sch127() {
        getFlowWithRest().step("SCH-127. Негативный фильтр: неразрешённый operatorCode",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch127()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-128. Допустимый пустой массив значений фильтра")
    void sch128() {
        getFlowWithRest().step("SCH-128. Допустимый пустой массив значений фильтра",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch128()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-129. Негативный фильтр: operatorCode=null")
    void sch129() {
        getFlowWithRest().step("SCH-129. Негативный фильтр: operatorCode=null",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch129()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-130. Негативный фильтр: нечисловое значение для числового поля")
    void sch130() {
        getFlowWithRest().step("SCH-130. Негативный фильтр: нечисловое значение для числового поля",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch130()).run();
    }



    @Regression
    @Test
    @DisplayName("SCH-134. Epoch millis round-trip")
    void sch134() {
        getFlowWithRest().step("SCH-134. Epoch millis round-trip",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch134()).run();
    }

    @Regression
    @Test
    @DisplayName("SCH-135. Health и metrics")
    void sch135() {
        getFlowWithRest().step("SCH-135. Health и metrics",
                flow -> flow.restCustomSteps().schedulerApiRegressionSteps().sch135()).run();
    }

}

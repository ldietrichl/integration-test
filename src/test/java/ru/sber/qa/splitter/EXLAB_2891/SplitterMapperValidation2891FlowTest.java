package ru.sber.qa.splitter.EXLAB_2891;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;
import steps.flow.splitter.mapper.MapperValidationSteps;

import config.environment.special.EnvironmentConfigWithMapperPrecalc;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import static org.junit.jupiter.api.Assertions.*;

@SetEnvironmentConfiguration(EnvironmentConfigWithMapperPrecalc.class)
@AnyConfigLoadMode
@DisplayName("EXLAB-2891. MAPPER: валидация предрасчёта")
public class SplitterMapperValidation2891FlowTest extends MapperValidationSteps {
    @Test @CriticalRegression
    @DisplayName("EXLAB-2891-T01. Валидный предрасчёт и использование сохранённых связей")
    void validRequest() {
        scenario("Предрасчёт двух объектов с разным результатом", f -> {
            load(f, exp(101, 1));
            calculate(f, pre(100));
            assertCounters(calculate(f, pre(101, preObject("U1", "gold"), preObject("U2", "silver"))),
                    0, 2, 0, 1, 2, 1, 1);
            preserved(f);
        });
    }

    @ParameterizedTest(name = "{0}") @CriticalRegression
    @ValueSource(strings = {"requestId:null", "requestId:empty", "requestId:missing",
            "splittingObjects:null", "splittingObjects:missing",
            "uniqueConfigurationId:null", "uniqueConfigurationId:empty", "uniqueConfigurationId:missing",
            "objectParams:null", "objectParams:missing", "objectParams:empty-array",
            "paramCode:null", "paramCode:empty", "paramCode:missing",
            "paramValues:null", "paramValues:missing", "dataType:null", "dataType:missing"})
    @DisplayName("EXLAB-2891-T02..07/T10. Отказ валидации сохраняет таблицу; следующий запрос успешен")
    void invalidField(String mutation) {
        scenario("Невалидное поле: " + mutation, f -> {
            seed(f);
            ObjectNode request = pre(102, preObject("NEW", "gold"));
            String[] parts = mutation.split(":");
            ObjectNode object = (ObjectNode) request.path("splittingObjects").get(0);
            ObjectNode owner = switch (parts[0]) {
                case "requestId", "splittingObjects" -> request;
                case "uniqueConfigurationId", "objectParams" -> object;
                default -> (ObjectNode) object.path("objectParams").get(0);
            };
            mutate(owner, parts[0], parts[1]);
            reject(f, request);
            assertNoResult(splitOne(f, "NEW", "silver"), "O1"); // no partially published NEW=gold
            preserved(f);
            assertCounters(calculate(f, pre(104, preObject("RECOVERED", "gold"))), 0, 1, 2, 0, 1, 1, 1);
            assertMainA(splitOne(f, "RECOVERED", "silver"), 101);
        });
    }

    @ParameterizedTest(name = "Ошибка во вложенном поле: {0}") @CriticalRegression
    @ValueSource(strings = {"uniqueConfigurationId", "paramCode", "dataType"})
    @DisplayName("EXLAB-2891-T08. Проверяется второй объект и последний вложенный параметр")
    void cascadeThroughAllElements(String field) {
        scenario("Каскадная валидация всего набора", f -> {
            seed(f);
            ObjectNode bad = preObject("BAD", "gold");
            if (field.equals("uniqueConfigurationId")) bad.putNull(field);
            else bad.withArray("objectParams").add(param("extra", "1").putNull(field));
            ObjectNode request = pre(102, preObject("NEW", "gold"), bad);
            reject(f, request);
            assertNoResult(splitOne(f, "NEW", "silver"), "O1");
            preserved(f);
        });
    }

    @Test @CriticalRegression
    @DisplayName("EXLAB-2891-T09/T25. Несколько нарушений: понятная ошибка без EXCEPTION")
    void multipleViolations() {
        scenario("Нарушения на двух уровнях DTO", f -> {
            seed(f);
            ObjectNode bad = preObject("BAD", "gold").putNull("uniqueConfigurationId");
            ((ObjectNode) bad.path("objectParams").get(0)).putNull("paramCode");
            reject(f, pre(102, bad));
            preserved(f);
        });
    }
}

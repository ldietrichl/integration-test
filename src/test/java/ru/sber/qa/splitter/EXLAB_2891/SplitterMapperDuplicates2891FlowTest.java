package ru.sber.qa.splitter.EXLAB_2891;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;
import steps.flow.splitter.mapper.MapperDuplicateSteps;

import config.environment.special.EnvironmentConfigWithMapperPrecalc;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;

@SetEnvironmentConfiguration(EnvironmentConfigWithMapperPrecalc.class)
@AnyConfigLoadMode
@DisplayName("EXLAB-2891. MAPPER: повторяющиеся объекты предрасчёта")
public class SplitterMapperDuplicates2891FlowTest extends MapperDuplicateSteps {
    @Test @CriticalRegression
    @DisplayName("EXLAB-2891-T11. Два одинаковых объекта configCommId=1646160")
    void incidentPayload() {
        scenario("Повтор параметров из исходного инцидента", f -> {
            ObjectNode experiment = exp(101, 1);
            ObjectNode rule = (ObjectNode) experiment.path("objectSelectConditions").get(0).path("rules").get(0).get(0);
            rule.put("paramCode", "configCommId").put("dataType", "INTEGER");
            rule.putArray("values").add("1646160");
            load(f, experiment);
            calculate(f, pre(100));
            ObjectNode object = incidentObject();
            assertCounters(calculate(f, pre(101, object, object.deepCopy())), 0, 1, 0, 0, 1, 1, 1);
            ObjectNode probe = obj("O1", null, "silver").put("uniqueConfigurationId", "1646160");
            probe.putArray("objectParams").add(param("configCommId", "999").put("dataType", "INTEGER"));
            assertMainA(split(f, request(S_B, probe)), 101);
        });
    }

    @ParameterizedTest(name = "Порядок набора: {0}") @CriticalRegression
    @ValueSource(strings = {"дубликаты в начале", "дубликаты в конце"})
    @DisplayName("EXLAB-2891-T12..14. Три повтора среди разных объектов; повторная отправка")
    void duplicatesMixedWithUniqueObjects(String order) {
        scenario("Счётчики учитывают уникальные ключи независимо от порядка", f -> {
            load(f, exp(101, 1));
            calculate(f, pre(100));
            ObjectNode linked = preObject("DUP", "gold");
            ObjectNode unmatched = preObject("OTHER", "silver");
            ObjectNode request = order.equals("дубликаты в конце")
                    ? pre(101, unmatched, linked, linked.deepCopy(), linked.deepCopy())
                    : pre(101, linked, linked.deepCopy(), linked.deepCopy(), unmatched);
            assertCounters(calculate(f, request), 0, 2, 0, 1, 2, 1, 1);
            assertMainA(splitOne(f, "DUP", "silver"), 101);
            assertNoResult(splitOne(f, "OTHER", "gold"), "O1");
            request.put("soConfigVersion", 102);
            assertCounters(calculate(f, request), 2, 0, 0, 1, 2, 1, 1);
            assertMainA(splitOne(f, "DUP", "silver"), 101);
            assertNoResult(splitOne(f, "OTHER", "gold"), "O1");
        });
    }
}

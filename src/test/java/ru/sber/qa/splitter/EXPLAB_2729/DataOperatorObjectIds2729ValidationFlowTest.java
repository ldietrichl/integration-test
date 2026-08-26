package ru.sber.qa.splitter.EXPLAB_2729;

import config.environment.EnvironmentConfigWithRest;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import util.dataoperator.DataOperatorAssertions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static request.dataoperator.DataOperatorTestDataFactory.DEFAULT_SPLITTING_POINT;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("data-operator-cache")
public class DataOperatorObjectIds2729ValidationFlowTest extends AbstractDataOperator2729FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-V-01. Отсутствующий splittingPointCode отклоняется с 400")
    void missingSplittingPointCodeShouldReturnBadRequest() {
        assertBadRequest(Map.of(
                "parent", false,
                "rules", List.of()
        ));
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-V-02. splittingPointCode=null отклоняется с 400")
    void nullSplittingPointCodeShouldReturnBadRequest() {
        Map<String, Object> body = validBody();
        body.put("splittingPointCode", null);
        assertBadRequest(body);
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-V-03. Отсутствующий rules отклоняется с 400")
    void missingRulesShouldReturnBadRequest() {
        assertBadRequest(Map.of(
                "splittingPointCode", DEFAULT_SPLITTING_POINT,
                "parent", false
        ));
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-V-04. rules=null отклоняется с 400")
    void nullRulesShouldReturnBadRequest() {
        Map<String, Object> body = validBody();
        body.put("rules", null);
        assertBadRequest(body);
    }

    @Test
    @DisplayName("DO-IDS-V-05. Пустая внутренняя группа rules=[[]] отклоняется с 400")
    void emptyInnerRuleGroupShouldReturnBadRequest() {
        Map<String, Object> body = validBody();
        body.put("rules", List.of(List.of()));
        assertBadRequest(body);
    }

    @Test
    @DisplayName("DO-IDS-V-06. Rule без dataType отклоняется с 400")
    void ruleWithoutDataTypeShouldReturnBadRequest() {
        assertBadRequest(bodyWithRule(Map.of(
                "parameterCode", "modelId",
                "operatorCode", "equal",
                "values", List.of("1")
        )));
    }

    @Test
    @DisplayName("DO-IDS-V-07. Rule без parameterCode отклоняется с 400")
    void ruleWithoutParameterCodeShouldReturnBadRequest() {
        assertBadRequest(bodyWithRule(Map.of(
                "dataType", "INTEGER",
                "operatorCode", "equal",
                "values", List.of("1")
        )));
    }

    @Test
    @DisplayName("DO-IDS-V-08. Rule без operatorCode отклоняется с 400")
    void ruleWithoutOperatorCodeShouldReturnBadRequest() {
        assertBadRequest(bodyWithRule(Map.of(
                "dataType", "INTEGER",
                "parameterCode", "modelId",
                "values", List.of("1")
        )));
    }

    @Test
    @DisplayName("DO-IDS-V-09. Неизвестный dataType отклоняется с 400")
    void unknownDataTypeShouldReturnBadRequest() {
        assertBadRequest(bodyWithRule(Map.of(
                "dataType", "UNSUPPORTED_TYPE",
                "parameterCode", "modelId",
                "operatorCode", "equal",
                "values", List.of("1")
        )));
    }

    @Test
    @DisplayName("DO-IDS-V-10. Неизвестный operatorCode отклоняется с 400")
    void unknownOperatorShouldReturnBadRequest() {
        assertBadRequest(bodyWithRule(Map.of(
                "dataType", "INTEGER",
                "parameterCode", "modelId",
                "operatorCode", "unsupported_operator",
                "values", List.of("1")
        )));
    }

    @Test
    @DisplayName("DO-IDS-V-11. Неверный JSON-тип parent отклоняется с 400")
    void invalidParentTypeShouldReturnBadRequest() {
        Map<String, Object> body = validBody();
        body.put("parent", "true");
        assertBadRequest(body);
    }

    @Test
    @DisplayName("DO-IDS-V-12. Неверный JSON-тип rules отклоняется с 400")
    void invalidRulesTypeShouldReturnBadRequest() {
        Map<String, Object> body = validBody();
        body.put("rules", "not-an-array");
        assertBadRequest(body);
    }

    @Test
    @DisplayName("DO-IDS-V-13. Более 255 групп rules отклоняется с 400")
    void moreThan255RuleGroupsShouldReturnBadRequest() {
        List<List<Map<String, Object>>> groups = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            groups.add(List.of(validRule()));
        }
        Map<String, Object> body = validBody();
        body.put("rules", groups);
        assertBadRequest(body);
    }

    private void assertBadRequest(Object body) {
        getFlowWithRest()
                .step("Отправляем невалидный запрос", flow -> {
                    var response = flow.restCustomSteps().dataOperatorSteps()
                            .getSplittingObjectIdsStatusBadRequest(body);
                    DataOperatorAssertions.shouldHaveSpecificationErrorEnvelope(response);
                })
                .run();
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("splittingPointCode", DEFAULT_SPLITTING_POINT);
        body.put("parent", false);
        body.put("rules", List.of());
        return body;
    }

    private static Map<String, Object> bodyWithRule(Map<String, Object> rule) {
        Map<String, Object> body = validBody();
        body.put("rules", List.of(List.of(rule)));
        return body;
    }

    private static Map<String, Object> validRule() {
        return Map.of(
                "dataType", "INTEGER",
                "parameterCode", "modelId",
                "operatorCode", "equal",
                "values", List.of("1")
        );
    }
}

package ru.sber.qa.splitter.NewTest.document;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
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

import java.util.List;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
/**
 * Сценарии этого класса зависят от ConfigMap splitter-mapper-service-lib / splitter-rules-mapper.yml:
 * - rules.filter-rule.enabled=true;
 * - rules.filter-rule.proc-params.values=[2, 4];
 * - rules.filtered-flag-rule.proc-params.flag-code=filtered.
 * Если на стенде изменится список filter-rule.values или имя filtered flag, ожидаемые значения
 * в assertFiltered(...) нужно актуализировать вместе с DisplayName тестов.
 */
@AnyConfigLoadMode
public class SplitterDocument03FilteredFlowTest extends AbstractSplitterDocumentFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DOC-03-01. filtered=false: actionType=0 не подавляет объект")
    void actionType0ShouldMarkObjectAsFiltered() {
        // ConfigMap-dependent: actionType=0 отсутствует в rules.filter-rule.values=[2,4],
        // поэтому filtered flag должен быть false.
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(
                10301,
                "DOC-03-01-SALT",
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с actionType=0", flow -> loadConfigStep(flow, config))
                .step("Выполняем split и проверяем filtered=false по ConfigMap filter-rule.values=[2,4]", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10301L);
                    assertFiltered(response, MATCHING_OBJECT_ID, "false");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-03-02. filtered=true: actionType=2 размечает объект как подавленный")
    void actionType2ShouldMarkObjectAsFiltered() {
        // ConfigMap-dependent: actionType=2 входит в rules.filter-rule.values=[2,4],
        // поэтому filtered flag должен быть true.
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(
                10302,
                "DOC-03-02-SALT",
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(fullRangeGroup("A", 1, "2"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с actionType=2", flow -> loadConfigStep(flow, config))
                .step("Выполняем split и проверяем filtered=true по ConfigMap filter-rule.values=[2,4]", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10302L);
                    assertFiltered(response, MATCHING_OBJECT_ID, "true");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-03-03. filtered вычисляется по итоговому MAIN")
    void filteredShouldBeCalculatedByMainExperimentOnly() {
        // ConfigMap-dependent:
        // - rules.final-exp-rule.proc-params.values-map задает приоритет actionType=2 выше actionType=0;
        // - rules.filter-rule.values=[2,4] задает filtered=true для выбранного MAIN с actionType=2.
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(10303, "DOC-03-03-SALT-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "0"))),
                experiment(10304, "DOC-03-03-SALT-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "2"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем два эксперимента: actionType=0 и actionType=2", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что MAIN и filtered выбраны по ConfigMap final-exp-rule/filter-rule", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10304L);
                    assertFiltered(response, MATCHING_OBJECT_ID, "true");
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 10303L, 10304L);
                })
                .run();
    }
}

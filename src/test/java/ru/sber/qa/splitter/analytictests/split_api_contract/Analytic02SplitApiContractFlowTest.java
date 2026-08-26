package ru.sber.qa.splitter.analytictests.split_api_contract;

import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import util.support.SplitterVersionProvider;

import java.util.List;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
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
public class Analytic02SplitApiContractFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Делаем корректный запрос на сплиттование с загруженной конфигурацией, проверяем корректность структуры ответа.")
    @DisplayName("AN-SPLIT-01. Корректный split возвращает базовый контракт ответа")
    void validSplitShouldReturnBaseResponseContract() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7201, "AN-SPLIT-01", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto split = splitRequest("AN-SPLIT-01", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с одним экспериментом", flow -> loadConfigStep(flow, config))
                .step("Проверяем базовую структуру split response", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 1);
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7201L);
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                    assertFiltered(response, MATCHING_OBJECT_ID, "false");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Ни один объект не привязался; результат сплиттования не должен содержать MAIN для объекта.")
    @DisplayName("AN-SPLIT-02. Запрос с объектом без привязки возвращает объект с пустым результатом или без MAIN")
    void unmatchedObjectShouldNotHaveMainAssignment() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7202, "AN-SPLIT-02", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto split = splitRequest("AN-SPLIT-02", object(NEGATIVE_OBJECT_ID, param("id", "999", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config, который не должен привязать negative object", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что объект не получил MAIN assignment", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, version);
                    assertObjectHasAllButNoMainAssignment(response, NEGATIVE_OBJECT_ID);
                })
                .run();
    }


    @Test
    @AnalyticTag("Аналитика: Делаем некорректный запрос на сплиттование. Должны получить ошибку.")
    @DisplayName("AN-SPLIT-03. Некорректный split request возвращает ошибку валидации")
    void invalidSplitRequestShouldReturnValidationError() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7203, "AN-SPLIT-03", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        String splitWithoutSplittingId = "{\"requestId\":\"AN-SPLIT-03\",\"requestParams\":[],\"splittingObjects\":[]}";
        String malformedJson = "{\"requestId\":\"AN-SPLIT-03\",\"splittingObjects\":[";

        getFlowWithRest()
                .step("Загружаем валидный config, чтобы изолировать проверку request validation", flow -> loadConfigStep(flow, config))
                .step("Проверяем ошибку для split без splittingId",
                        flow -> split(flow, splitWithoutSplittingId).should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем ошибку для syntactically invalid JSON",
                        flow -> split(flow, malformedJson).should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .run();
    }

}

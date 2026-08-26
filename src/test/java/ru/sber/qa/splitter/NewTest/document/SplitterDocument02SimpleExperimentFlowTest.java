package ru.sber.qa.splitter.NewTest.document;

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
public class SplitterDocument02SimpleExperimentFlowTest extends AbstractSplitterDocumentFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DOC-02-01. простой эксперимент: клиент в группе A получает MAIN и ALL")
    void simpleExperimentShouldReturnMainAndAllForGroupA() {
        long version = SplitterVersionProvider.next();
        String splittingId = "123456";
        int expectedSpread = spread("24096d2M1e", splittingId);
        LoadConfigRequestDto config = config(version, experiment(
                10201,
                "24096d2M1e",
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest(splittingId,
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с одним экспериментом и группой A 0..10000", flow -> loadConfigStep(flow, config))
                .step("Выполняем split для объекта, совпадающего с condition", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertSplittingResultsSize(response, 1);
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10201L);
                    assertMainGroup(response, MATCHING_OBJECT_ID, "A");
                    assertMainActionType(response, MATCHING_OBJECT_ID, "0");
                    assertMainSpreadValue(response, MATCHING_OBJECT_ID, expectedSpread);
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 10201L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-02-02. простой эксперимент: объект вне condition возвращается с пустым objectResults")
    void simpleExperimentShouldReturnEmptyResultsForNonMatchingObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(
                10202,
                "DOC-02-02-SALT",
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(NEGATIVE_OBJECT_ID, param("id", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем простой конфиг", flow -> loadConfigStep(flow, config))
                .step("Выполняем split для объекта, не совпадающего с condition", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertObjectResultsEmpty(response, NEGATIVE_OBJECT_ID);
                })
                .run();
    }
}

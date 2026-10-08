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
@AnyConfigLoadMode
public class SplitterDocument06AlgorithmExamplesFlowTest extends AbstractSplitterDocumentFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DOC-06-01. вычисление групп: несколько диапазонов, разрывы и попадание вне диапазона")
    void groupCalculationShouldSupportMultipleRangesAndGaps() {
        long version = SplitterVersionProvider.next();
        String salt = "DOC-06-GROUP-SALT";
        LoadConfigRequestDto config = config(version, experiment(
                10601,
                salt,
                List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                List.of(
                        group("A", 0, 1000, 1, "1"),
                        group("B", 2000, 5000, 1, "2"),
                        group("C", 9000, 10000, 1, "3"))));

        getFlowWithRest()
                .step("Загружаем конфиг с группами A/B/C и разрывами диапазонов", flow -> loadConfigStep(flow, config))
                .step("Проверяем попадание в нижний диапазон A", flow -> assertGroup(flow, version, "DOC10", "A", "1", spread(salt, "DOC10")))
                .step("Проверяем попадание в средний диапазон B", flow -> assertGroup(flow, version, "DOC0", "B", "2", spread(salt, "DOC0")))
                .step("Проверяем попадание в верхний диапазон C", flow -> assertGroup(flow, version, "DOC4", "C", "3", spread(salt, "DOC4")))
                .step("Проверяем попадание в свободный разрыв без сработавшей группы", flow -> {
                    SplitRequestDto request = splitRequest("DOC1", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertObjectHasAllButNoMainAssignment(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    private void assertGroup(FlowWithRest flow, long version, String splittingId, String expectedGroup, String expectedActionType, int expectedSpread) {
        SplitRequestDto request = splitRequest(splittingId, object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));
        ValidatableResponseWrapper response = shouldBe200(split(flow, request));
        assertConfigVersion(response, version);
        assertMainExp(response, MATCHING_OBJECT_ID, 10601L);
        assertMainGroup(response, MATCHING_OBJECT_ID, expectedGroup);
        assertMainActionType(response, MATCHING_OBJECT_ID, expectedActionType);
        assertMainSpreadValue(response, MATCHING_OBJECT_ID, expectedSpread);
    }
}

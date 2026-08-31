package ru.sber.qa.splitter.EXPLAB_2690;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
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
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.support.SplitterTestProfileOnly;
import util.support.SplitterVersionProvider;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2690. MAPPER: независимая обработка смешанного набора объектов")
@AnyConfigLoadMode
public class SplitterMapperMixedObjects2690FlowTest extends AbstractExplab2690FlowTest {

    private static final String NO_MAIN_OBJECT_ID = "26900000-0000-0000-0000-000000000005";
    private static final String UNLINKED_OBJECT_ID = "26900000-0000-0000-0000-000000000006";

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2690-08-CURRENT. Обычный, no-MAIN и несвязанный объекты не влияют друг на друга")
    void mapperShouldProcessNormalNoMainAndUnlinkedObjectsIndependentlyOnCurrentSdk() {
        long version = SplitterVersionProvider.next();
        String splittingId = splittingIdForRange("EXPLAB-2690-08-CURRENT", SALT_2690, 0, 5000);
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER, version,
                alternativeExperiment(), noMainExperiment());
        SplitRequestDto request = splitRequest(splittingId,
                object(LEFT_OBJECT_ID, param("left", "1", "INTEGER")),
                object(NO_MAIN_OBJECT_ID, param("noMain", "1", "INTEGER")),
                object(UNLINKED_OBJECT_ID, param("unlinked", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config с обычным объектом, no-MAIN и несвязанным объектом",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем независимый REST-результат без target-contract альтернативы", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertSplittingResultsHaveUniqueObjectIds(response);

                    assertResultExp(response, LEFT_OBJECT_ID, "MAIN", 269011L, 1,
                            "A", "A", "1", "101");
                    assertResultExp(response, LEFT_OBJECT_ID, "ALL", 269011L, 1,
                            "A", "A", "1", "101");
                    assertMainHasNoExpFlags(response, LEFT_OBJECT_ID);

                    assertObjectHasStrictlyEmptyResult(response, NO_MAIN_OBJECT_ID);
                    assertRuleAbsent(response, NO_MAIN_OBJECT_ID, "MAIN");
                    assertRuleAbsent(response, NO_MAIN_OBJECT_ID, "ALL");

                    assertObjectHasStrictlyEmptyResult(response, UNLINKED_OBJECT_ID);
                    assertRuleAbsent(response, UNLINKED_OBJECT_ID, "MAIN");
                    assertRuleAbsent(response, UNLINKED_OBJECT_ID, "ALL");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @SplitterTestProfileOnly("mapper-alternative-contract")
    @DisplayName("EXPLAB-2690-08. Обычный, альтернативный, no-MAIN и несвязанный объекты не влияют друг на друга")
    void mapperShouldProcessNormalAlternativeNoMainAndUnlinkedObjectsIndependently() {
        long version = SplitterVersionProvider.next();
        String splittingId = splittingIdForRange("EXPLAB-2690-08-MIXED", SALT_2690, 0, 5000);
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER, version,
                alternativeExperiment(), noMainExperiment());
        SplitRequestDto request = splitRequest(splittingId,
                object(LEFT_OBJECT_ID, param("left", "1", "INTEGER")),
                object(RIGHT_OBJECT_ID, param("right", "1", "INTEGER")),
                object(NO_MAIN_OBJECT_ID, param("noMain", "1", "INTEGER")),
                object(UNLINKED_OBJECT_ID, param("unlinked", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config со штатной альтернативой и отдельным no-MAIN экспериментом",
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем независимый REST-результат четырёх объектов", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertSplittingResultsHaveUniqueObjectIds(response);

                    assertResultExp(response, LEFT_OBJECT_ID, "MAIN", 269011L, 1,
                            "A", "A", "1", "101");
                    assertResultExp(response, LEFT_OBJECT_ID, "ALL", 269011L, 1,
                            "A", "A", "1", "101");
                    assertMainHasNoExpFlags(response, LEFT_OBJECT_ID);

                    assertResultExp(response, RIGHT_OBJECT_ID, "MAIN", 269011L, 2,
                            "B", "A", "3", "202");
                    assertMainHasNoExpFlags(response, RIGHT_OBJECT_ID);
                    assertRuleAbsent(response, RIGHT_OBJECT_ID, "ALL");

                    assertObjectHasStrictlyEmptyResult(response, NO_MAIN_OBJECT_ID);
                    assertRuleAbsent(response, NO_MAIN_OBJECT_ID, "MAIN");
                    assertRuleAbsent(response, NO_MAIN_OBJECT_ID, "ALL");

                    assertObjectHasStrictlyEmptyResult(response, UNLINKED_OBJECT_ID);
                    assertRuleAbsent(response, UNLINKED_OBJECT_ID, "MAIN");
                    assertRuleAbsent(response, UNLINKED_OBJECT_ID, "ALL");
                })
                .run();
    }

    private ExperimentDto alternativeExperiment() {
        return experiment(269011,
                SALT_2690,
                List.of(
                        objectParamEqualsCondition(1, "left", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "right", "1", "INTEGER")),
                List.of(
                        groupWithDocResult("A", shares(0, 5000), 1, "1", "101"),
                        groupWithDocResult("B", shares(5000, 10000), 2, "3", "202")));
    }

    private ExperimentDto noMainExperiment() {
        return experiment(269012,
                SALT_2690 + "-NO-MAIN",
                List.of(objectParamEqualsCondition(1, "noMain", "1", "INTEGER")),
                List.of(group("A", shares(0, 10000), List.of(
                        resultWithParams(1, param("result", "999", "INTEGER"))))));
    }
}

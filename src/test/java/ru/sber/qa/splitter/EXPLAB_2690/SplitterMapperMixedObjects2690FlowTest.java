package ru.sber.qa.splitter.EXPLAB_2690;
import steps.flow.splitter.workedgroup.MapperMixedObjectsSteps;

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
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.support.SplitterTestProfileOnly;
import util.support.SplitterVersionProvider;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2690. MAPPER: независимая обработка смешанного набора объектов")
@AnyConfigLoadMode
@org.junit.jupiter.api.Order(10)
public class SplitterMapperMixedObjects2690FlowTest extends MapperMixedObjectsSteps {

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
                    org.junit.jupiter.api.Assertions.assertAll("Independent object contracts",
                    () -> assertBasicResponseContract(response, request, version),
                    () -> {
                    assertResultExp(response, LEFT_OBJECT_ID, "MAIN", 269011L, 1,
                            "A", "A", "1", "101");
                    assertResultExp(response, LEFT_OBJECT_ID, "ALL", 269011L, 1,
                            "A", "A", "1", "101");
                    assertMainHasNoExpFlags(response, LEFT_OBJECT_ID);
                    }, () -> {
                    assertObjectWithoutMain(response, NO_MAIN_OBJECT_ID, java.util.Map.of(269012L, "A"));
                    }, () -> {
                    assertObjectHasStrictlyEmptyResult(response, UNLINKED_OBJECT_ID);
                    assertRuleAbsent(response, UNLINKED_OBJECT_ID, "MAIN");
                    assertRuleAbsent(response, UNLINKED_OBJECT_ID, "ALL");
                    });
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
                    org.junit.jupiter.api.Assertions.assertAll("Independent object contracts",
                    () -> assertBasicResponseContract(response, request, version),
                    () -> {
                    assertResultExp(response, LEFT_OBJECT_ID, "MAIN", 269011L, 1,
                            "A", "A", "1", "101");
                    assertResultExp(response, LEFT_OBJECT_ID, "ALL", 269011L, 1,
                            "A", "A", "1", "101");
                    assertMainHasNoExpFlags(response, LEFT_OBJECT_ID);
                    }, () -> {
                    assertResultExp(response, RIGHT_OBJECT_ID, "MAIN", 269011L, 2,
                            "B", "A", "3", "202");
                    assertMainHasNoExpFlags(response, RIGHT_OBJECT_ID);
                    assertRuleAbsent(response, RIGHT_OBJECT_ID, "ALL");
                    }, () -> {
                    assertObjectWithoutMain(response, NO_MAIN_OBJECT_ID, java.util.Map.of(269012L, "A"));
                    }, () -> {
                    assertObjectHasStrictlyEmptyResult(response, UNLINKED_OBJECT_ID);
                    assertRuleAbsent(response, UNLINKED_OBJECT_ID, "MAIN");
                    assertRuleAbsent(response, UNLINKED_OBJECT_ID, "ALL");
                    });
                })
                .run();
    }
}

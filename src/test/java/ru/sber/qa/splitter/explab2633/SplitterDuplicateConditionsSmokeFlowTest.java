package ru.sber.qa.splitter.explab2633;

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
import ru.sber.qa.splitter.EXPLAB_2633.AbstractExplab2633DuplicateConditionsFlowTest;
import util.support.SplitterVersionProvider;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@AnyConfigLoadMode
public class SplitterDuplicateConditionsSmokeFlowTest extends AbstractExplab2633DuplicateConditionsFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("SPL-01. Конфиг без дублей загружается и split возвращает результат")
    void configWithoutDuplicateParamsShouldLoadAndSplit() {
        long version = SplitterVersionProvider.next();
        int expId = 263301;
        LoadConfigRequestDto config = duplicateRulesConfig(version, expId, "SPL-01", andGroup(
                objectIntRule("param1", "equal", "2"),
                objectStringRule("param2", "equal", "FFF")));
        SplitRequestDto request = splitRequest("SPL-01", object(OBJECT_ID,
                intParam("param1", "2"),
                stringParam("param2", "FFF")));

        getFlowWithRest()
                .step("Загружаем config SPL-01 без дублей paramCode", flow -> loadConfigStep(flow, config))
                .step("Выполняем split и проверяем базовую работоспособность стенда", flow -> {
                    var response = shouldBe200(split(flow, request));
                    assertSuccessfulSplitResponse(response, version);
                    assertObjectMatchedExperiment(response, OBJECT_ID, expId);
                })
                .run();
    }
}

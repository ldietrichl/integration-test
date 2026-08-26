package ru.sber.qa.splitter.EXPLAB_2398;


import ru.sber.qa.allure.ManualTest;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@ManualTest
public class SplitterPrecalcMonitoring2398ManualFlowTest extends AbstractPrecalcMonitoring2398FlowTest {

    @CriticalRegression
    @Disabled("Требует отдельный стенд/ConfigMap с выключенным параметром 'Предрасчет включен = false'. На обычном dev regression не запускать.")
    @Test
    @DisplayName("EXPLAB-2398-PC-06. PRECALC_NOT_ENABLED пишет REQUEST_REJECTED_PRECALC_NOT_ENABLED")
    void disabledPrecalcShouldWriteRejectedMonitoringEvent(KafkaService kafkaService) {
        int soVersion = nextSoConfigVersion();
        SplitterPrecalcRequestDto request = precalcRequest(soVersion, matchingPrecalcObject(uc("pc05", 1)));

        getFlowWithRest()
                .step("При выключенном предрасчете метод должен вернуть PRECALC_NOT_ENABLED", flow -> {
                    long since = System.currentTimeMillis();
                    ValidatableResponseWrapper response = flow.restCustomSteps().splitterSteps().calculatePreliminary(request);
                    response.should(haveStatusCode(HttpStatus.SC_BAD_REQUEST));
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("errorCode == 'PRECALC_NOT_ENABLED'"));
                    assertMonitoringEvent(kafkaService, since, precalcNotEnabledExpectation(request));
                })
                .run();
    }
}

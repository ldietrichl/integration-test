package ru.sber.qa.pilot;

import config.environment.special.EnvironmentConfigWithRestV2;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.pilot.PilotTestDataFactory;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static util.pilot.PilotAssertions.shouldHaveErrorDto;
import static util.pilot.PilotAssertions.shouldHavePilotDto;
import static util.pilot.PilotAssertions.shouldHavePilotNameAndStatus;
import static util.pilot.PilotAssertions.shouldHaveRegistryEnvelope;
import static util.pilot.PilotAssertions.shouldNotContainPilot;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRestV2.class)
@ResourceLock("pilot-service-regression")
@Regression
public class PilotStatusDeleteFlowTest extends AbstractPilotFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("PILOT-REG-024. Удаление DRAFT пилота удаляет созданную заявку")
    void deleteDraftPilotShouldRemovePilot() {
        String prefix = PilotTestDataFactory.uniquePrefix();
        String pilotName = prefix + "-DELETE";
        Long[] pilotId = new Long[1];

        getFlowWithRest()
                .step("Создаем DRAFT пилот для удаления", flow -> pilotId[0] = createDraftPilot(flow, pilotName))
                .step("Удаляем созданный DRAFT пилот", flow -> {
                    flow.restCustomSteps().pilotSteps().deletePilotByIdStatusOk(pilotId[0]);
                    forgetPilot(pilotId[0]);
                })
                .step("Проверяем, что удаленный пилот не возвращается в реестре по search", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .getPilotRegistryStatusOk(PilotTestDataFactory.registrySearch(prefix));
                    shouldHaveRegistryEnvelope(response);
                    shouldNotContainPilot(response, pilotId[0]);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PILOT-REG-038. Обновление статуса пилота возвращает актуальный DTO")
    void updatePilotStatusShouldReturnActualDto() {
        String pilotName = PilotTestDataFactory.uniquePrefix() + "-STATUS";
        Long[] pilotId = new Long[1];

        getFlowWithRest()
                .step("Создаем DRAFT пилот для смены статуса", flow -> pilotId[0] = createDraftPilot(flow, pilotName))
                .step("Вызываем endpoint смены статуса, сохраняя DRAFT для последующей очистки", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .updatePilotStatusOk(pilotId[0], PilotTestDataFactory.keepDraftStatusRequest());
                    shouldHavePilotDto(response);
                    shouldHavePilotNameAndStatus(response, pilotName, "DRAFT");
                })
                .run();
    }

    @Test
    @DisplayName("PILOT-REG-039. Обновление статуса без launchStatus отклоняется валидацией")
    void updatePilotStatusWithoutLaunchStatusShouldBeRejected() {
        String pilotName = PilotTestDataFactory.uniquePrefix() + "-STATUS-NEG";
        Long[] pilotId = new Long[1];

        getFlowWithRest()
                .step("Создаем DRAFT пилот для негативной проверки статуса", flow -> pilotId[0] = createDraftPilot(flow, pilotName))
                .step("Отправляем запрос смены статуса без launchStatus", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .updatePilotStatusBadRequest(pilotId[0], PilotTestDataFactory.statusWithoutLaunchStatus());
                    shouldHaveErrorDto(response);
                })
                .run();
    }
}

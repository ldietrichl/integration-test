package ru.sber.qa.pilot;

import config.environment.special.EnvironmentConfigWithRestV2;
import dto.pilot.PilotRequestDto;
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

import static util.pilot.PilotAssertions.shouldHaveEmptyLinkedArrays;
import static util.pilot.PilotAssertions.shouldHaveErrorDto;
import static util.pilot.PilotAssertions.shouldHavePilotDto;
import static util.pilot.PilotAssertions.shouldHavePilotNameAndStatus;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRestV2.class)
@ResourceLock("pilot-service-regression")
@Regression
public class PilotCreateUpdateFlowTest extends AbstractPilotFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("PILOT-REG-001. Создание минимального DRAFT пилота возвращает базовый DTO")
    void createMinimalDraftPilotShouldReturnBaseDto() {
        String pilotName = PilotTestDataFactory.uniquePrefix() + "-MIN";

        getFlowWithRest()
                .step("Создаем минимальную заявку на пилот в статусе DRAFT", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .createOrUpdatePilotStatusOk(PilotTestDataFactory.minimalDraftPilot(pilotName));
                    Long pilotId = response.toJsonPath().getLong("id");
                    shouldHavePilotDto(response);
                    shouldHavePilotNameAndStatus(response, pilotName, "DRAFT");
                    shouldHaveEmptyLinkedArrays(response);
                    forgetPilot(pilotId);
                    flow.restCustomSteps().pilotSteps().deletePilotByIdStatusOk(pilotId);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PILOT-REG-007. Обновление собственного DRAFT пилота меняет данные той же заявки")
    void updateOwnDraftPilotShouldChangeSamePilot() {
        String prefix = PilotTestDataFactory.uniquePrefix();
        String initialName = prefix + "-OLD";
        String updatedName = prefix + "-NEW";
        Long[] pilotId = new Long[1];

        getFlowWithRest()
                .step("Создаем DRAFT пилот для обновления", flow -> pilotId[0] = createDraftPilot(flow, initialName))
                .step("Обновляем имя созданного пилота", flow -> {
                    ValidatableResponseWrapper response = updatePilotName(flow, pilotId[0], updatedName);
                    shouldHavePilotDto(response);
                    shouldHavePilotNameAndStatus(response, updatedName, "DRAFT");
                })
                .step("Получаем пилот по id и проверяем, что изменение сохранилось", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps().getPilotByIdStatusOk(pilotId[0]);
                    shouldHavePilotNameAndStatus(response, updatedName, "DRAFT");
                })
                .run();
    }

    @Test
    @DisplayName("PILOT-REG-003. Создание пилота без name отклоняется валидацией")
    void createPilotWithoutNameShouldBeRejected() {
        getFlowWithRest()
                .step("Отправляем запрос без обязательного name", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .createOrUpdatePilotStatusBadRequest(PilotTestDataFactory.withoutName());
                    shouldHaveErrorDto(response);
                })
                .run();
    }

    @Test
    @DisplayName("PILOT-REG-004. Создание пилота с неизвестным launchStatus отклоняется валидацией")
    void createPilotWithUnknownStatusShouldBeRejected() {
        PilotRequestDto request = PilotTestDataFactory.withInvalidStatus(PilotTestDataFactory.uniquePrefix() + "-BAD-STATUS");

        getFlowWithRest()
                .step("Отправляем запрос с launchStatus вне enum", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .createOrUpdatePilotStatusBadRequest(request);
                    shouldHaveErrorDto(response);
                })
                .run();
    }
}

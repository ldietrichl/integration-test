package ru.sber.qa.pilot;

import config.environment.special.EnvironmentConfigWithRestV2;
import dto.pilot.PilotRegistryRequestDto;
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

import static util.pilot.PilotAssertions.shouldBeSortedByNameAsc;
import static util.pilot.PilotAssertions.shouldContainOnlyNamesWithSearch;
import static util.pilot.PilotAssertions.shouldContainPilot;
import static util.pilot.PilotAssertions.shouldHaveContentAtMost;
import static util.pilot.PilotAssertions.shouldHaveErrorDto;
import static util.pilot.PilotAssertions.shouldHavePilotDto;
import static util.pilot.PilotAssertions.shouldHavePilotNameAndStatus;
import static util.pilot.PilotAssertions.shouldHaveRegistryEnvelope;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRestV2.class)
@ResourceLock("pilot-service-regression")
@Regression
public class PilotGetRegistryFlowTest extends AbstractPilotFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("PILOT-REG-012. Получение пилота по id возвращает DTO созданной заявки")
    void getPilotByIdShouldReturnCreatedPilot() {
        String pilotName = PilotTestDataFactory.uniquePrefix() + "-GET";
        Long[] pilotId = new Long[1];

        getFlowWithRest()
                .step("Создаем DRAFT пилот", flow -> pilotId[0] = createDraftPilot(flow, pilotName))
                .step("Получаем пилот по id", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps().getPilotByIdStatusOk(pilotId[0]);
                    shouldHavePilotDto(response);
                    shouldHavePilotNameAndStatus(response, pilotName, "DRAFT");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PILOT-REG-017/019. Реестр пилотов находит созданную заявку по search")
    void registrySearchShouldReturnCreatedPilot() {
        String prefix = PilotTestDataFactory.uniquePrefix();
        String pilotName = prefix + "-REG";
        Long[] pilotId = new Long[1];

        getFlowWithRest()
                .step("Создаем пилот для проверки реестра", flow -> pilotId[0] = createDraftPilot(flow, pilotName))
                .step("Запрашиваем реестр по search", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .getPilotRegistryStatusOk(PilotTestDataFactory.registrySearch(prefix.toLowerCase()));
                    shouldHaveRegistryEnvelope(response);
                    shouldContainOnlyNamesWithSearch(response, prefix);
                    shouldContainPilot(response, pilotId[0], pilotName);
                })
                .run();
    }

    @Test
    @DisplayName("PILOT-REG-018. Реестр пилотов соблюдает ограничение page/size")
    void registryShouldRespectPageSize() {
        getFlowWithRest()
                .step("Запрашиваем первую страницу реестра", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .getPilotRegistryStatusOk(PilotTestDataFactory.firstPageRegistry());
                    shouldHaveRegistryEnvelope(response);
                    shouldHaveContentAtMost(response, 10);
                })
                .run();
    }

    @Test
    @DisplayName("PILOT-REG-021. Реестр пилотов сортируется по name ASC")
    void registryShouldSortByNameAsc() {
        String prefix = PilotTestDataFactory.uniquePrefix();

        getFlowWithRest()
                .step("Создаем пилоты с общим префиксом и разными именами", flow -> {
                    createDraftPilot(flow, prefix + "-BETA");
                    createDraftPilot(flow, prefix + "-ALPHA");
                })
                .step("Запрашиваем реестр с сортировкой по name ASC", flow -> {
                    PilotRegistryRequestDto request = PilotTestDataFactory.registrySortedByNameAsc(prefix);
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps().getPilotRegistryStatusOk(request);
                    shouldHaveRegistryEnvelope(response);
                    shouldContainOnlyNamesWithSearch(response, prefix);
                    shouldBeSortedByNameAsc(response);
                })
                .run();
    }

    @Test
    @DisplayName("PILOT-REG-023. Некорректный запрос реестра пилотов отклоняется валидацией")
    void invalidRegistryRequestShouldBeRejected() {
        getFlowWithRest()
                .step("Отправляем некорректные page/size", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                            .getPilotRegistryStatusBadRequest(PilotTestDataFactory.invalidRegistryRequest());
                    shouldHaveErrorDto(response);
                })
                .run();
    }
}

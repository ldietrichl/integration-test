package ru.sber.qa.pilot;

import dto.pilot.PilotRequestDto;
import flow.Flows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import request.pilot.PilotTestDataFactory;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.ArrayList;
import java.util.List;

import static io.qameta.allure.Allure.step;

public abstract class AbstractPilotFlowTest extends Flows {
    private final List<Long> createdPilotIds = new ArrayList<>();

    @AfterEach
    void cleanupPilots() {
        if (createdPilotIds.isEmpty()) {
            return;
        }
        step("Удаляем тестовые заявки на пилот", () -> createdPilotIds.forEach(id -> {
            try {
                getFlowWithRest().flow().restCustomSteps().pilotSteps().deletePilotById(id);
            } catch (RuntimeException ignored) {
                // cleanup не должен маскировать основную ошибку теста
            }
        }));
        createdPilotIds.clear();
    }

    protected Long createDraftPilot(FlowWithRest flow, String name) {
        ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                .createOrUpdatePilotStatusOk(PilotTestDataFactory.minimalDraftPilot(name));
        Long pilotId = response.toJsonPath().getLong("id");
        createdPilotIds.add(pilotId);
        return pilotId;
    }

    protected Long createDraftPilotWithMainFields(FlowWithRest flow, String name) {
        ValidatableResponseWrapper response = flow.restCustomSteps().pilotSteps()
                .createOrUpdatePilotStatusOk(PilotTestDataFactory.draftPilotWithMainFields(name));
        Long pilotId = response.toJsonPath().getLong("id");
        createdPilotIds.add(pilotId);
        return pilotId;
    }

    protected ValidatableResponseWrapper updatePilotName(FlowWithRest flow, Long pilotId, String newName) {
        PilotRequestDto request = PilotTestDataFactory.minimalDraftPilot(newName).toBuilder()
                .id(pilotId)
                .build();
        return flow.restCustomSteps().pilotSteps()
                .createOrUpdatePilot(request)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    protected void forgetPilot(Long pilotId) {
        createdPilotIds.remove(pilotId);
    }
}

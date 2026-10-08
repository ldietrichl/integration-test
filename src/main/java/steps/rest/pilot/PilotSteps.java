package steps.rest.pilot;

import constants.Endpoints;
import dto.pilot.PilotRegistryRequestDto;
import dto.pilot.PilotRequestDto;
import dto.pilot.PilotStatusUpdateRequestDto;
import dto.pilot.PilotValidateCampaignsRequestDto;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static io.qameta.allure.Allure.step;

public class PilotSteps {
    private final RestClient client;

    public PilotSteps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper createOrUpdatePilot(PilotRequestDto body) {
        return step("Создаем/обновляем пилот", () -> client.post(
                spec -> spec.body(PilotRequestDto.toJson(body)),
                Endpoints.Pilots.PILOTS));
    }

    public ValidatableResponseWrapper createOrUpdatePilotStatusOk(PilotRequestDto body) {
        return step("Проверяем, что создание/обновление пилота вернуло 200 OK", () -> createOrUpdatePilot(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper createOrUpdatePilotStatusBadRequest(PilotRequestDto body) {
        return step("Проверяем, что создание/обновление пилота вернуло 400 BAD_REQUEST", () -> createOrUpdatePilot(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public ValidatableResponseWrapper getPilotById(Long id) {
        return step("Получаем пилот по id=%s".formatted(id), () -> client.get(
                spec -> spec.pathParam("id", id),
                Endpoints.Pilots.PILOT_BY_ID));
    }

    public ValidatableResponseWrapper getPilotByIdStatusOk(Long id) {
        return step("Проверяем, что получение пилота вернуло 200 OK", () -> getPilotById(id)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getPilotRegistry(PilotRegistryRequestDto body) {
        return step("Запрашиваем реестр пилотов", () -> client.post(
                spec -> spec.body(PilotRegistryRequestDto.toJson(body)),
                Endpoints.Pilots.PILOT_REGISTRY));
    }

    public ValidatableResponseWrapper getPilotRegistryStatusOk(PilotRegistryRequestDto body) {
        return step("Проверяем, что реестр пилотов вернул 200 OK", () -> getPilotRegistry(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getPilotRegistryStatusBadRequest(PilotRegistryRequestDto body) {
        return step("Проверяем, что реестр пилотов вернул 400 BAD_REQUEST", () -> getPilotRegistry(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public ValidatableResponseWrapper getShortRegistry(PilotRegistryRequestDto body) {
        return step("Запрашиваем краткий реестр пилотов", () -> client.post(
                spec -> spec.body(PilotRegistryRequestDto.toJson(body)),
                Endpoints.Pilots.PILOT_SHORT_REGISTRY));
    }

    public ValidatableResponseWrapper getShortRegistryStatusOk(PilotRegistryRequestDto body) {
        return step("Проверяем, что краткий реестр пилотов вернул 200 OK", () -> getShortRegistry(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper deletePilotById(Long id) {
        return step("Удаляем пилот id=%s".formatted(id), () -> client.delete(
                spec -> spec.pathParam("id", id),
                Endpoints.Pilots.PILOT_BY_ID));
    }

    public ValidatableResponseWrapper deletePilotByIdStatusOk(Long id) {
        return step("Проверяем, что удаление пилота вернуло 200 OK", () -> deletePilotById(id)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper updatePilotStatus(Long id, PilotStatusUpdateRequestDto body) {
        return step("Обновляем статус пилота id=%s".formatted(id), () -> client.put(
                spec -> spec.pathParam("id", id).body(PilotStatusUpdateRequestDto.toJson(body)),
                Endpoints.Pilots.PILOT_STATUS));
    }

    public ValidatableResponseWrapper updatePilotStatusOk(Long id, PilotStatusUpdateRequestDto body) {
        return step("Проверяем, что обновление статуса пилота вернуло 200 OK", () -> updatePilotStatus(id, body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper updatePilotStatusBadRequest(Long id, PilotStatusUpdateRequestDto body) {
        return step("Проверяем, что обновление статуса пилота вернуло 400 BAD_REQUEST", () -> updatePilotStatus(id, body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public ValidatableResponseWrapper getLinkedExperiments(Long id) {
        return step("Получаем связанные эксперименты пилота id=%s".formatted(id), () -> client.get(
                spec -> spec.pathParam("id", id),
                Endpoints.Pilots.PILOT_LINKED_EXPERIMENTS));
    }

    public ValidatableResponseWrapper getLinkedCampaigns(Long id) {
        return step("Получаем связанные кампании пилота id=%s".formatted(id), () -> client.get(
                spec -> spec.pathParam("id", id),
                Endpoints.Pilots.PILOT_LINKED_CAMPAIGNS));
    }

    public ValidatableResponseWrapper validatePilotCampaigns(PilotValidateCampaignsRequestDto body) {
        return step("Валидируем список кампаний для привязки к пилоту", () -> client.post(
                spec -> spec.body(PilotValidateCampaignsRequestDto.toJson(body)),
                Endpoints.Pilots.PILOT_VALIDATE_CAMPAIGNS));
    }
}

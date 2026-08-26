package steps.rest.experiments.v2.layers;

import constants.Endpoints;
import dto.experiments.layers.LayerGetChangeRequestDto;
import dto.experiments.v2.layers.LayerRegistryV2RequestDto;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static io.qameta.allure.Allure.step;

public class LayerV2Steps {

    private final RestClient client;

    public LayerV2Steps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper createOrChangeLayer(LayerGetChangeRequestDto body) {
        return step("Создаем/изменяем слой через v2", () -> client.post(
                spec -> spec.body(body),
                Endpoints.LayersV2.V2_LAYERS));
    }

    public ValidatableResponseWrapper getLayerById(Long id) {
        return step("Получаем слой по id через v2: %s".formatted(id), () -> client.get(
                spec -> spec.pathParam("id", id),
                Endpoints.LayersV2.V2_LAYERS_ID));
    }

    public ValidatableResponseWrapper deleteLayerById(Long id) {
        return step("Удаляем слой через v2: %s".formatted(id), () -> client.delete(
                spec -> spec.pathParam("id", id),
                Endpoints.LayersV2.V2_LAYERS_ID));
    }

    public ValidatableResponseWrapper startLayerById(Long id) {
        return step("Включаем слой через v2: %s".formatted(id), () -> client.put(
                spec -> spec.pathParam("id", id),
                Endpoints.LayersV2.V2_LAYERS_ID_START));
    }

    public ValidatableResponseWrapper startLayerByIdStatusOk(Long id) {
        return step("Проверяем, что слой включен через v2", () -> startLayerById(id)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper stopLayerById(Long id) {
        return step("Выключаем слой через v2: %s".formatted(id), () -> client.put(
                spec -> spec.pathParam("id", id),
                Endpoints.LayersV2.V2_LAYERS_ID_STOP));
    }

    public ValidatableResponseWrapper stopLayerByIdStatusOk(Long id) {
        return step("Проверяем, что слой выключен через v2", () -> stopLayerById(id)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getLayerRegistry(LayerRegistryV2RequestDto body) {
        return step("Запрашиваем реестр слоев v2", () -> client.post(
                spec -> spec.body(LayerRegistryV2RequestDto.toJson(body)),
                Endpoints.LayersV2.V2_LAYERS_REGISTRY));
    }

    public ValidatableResponseWrapper getLayerRegistryStatusOk(LayerRegistryV2RequestDto body) {
        return step("Проверяем, что реестр слоев v2 вернул 200 OK", () -> getLayerRegistry(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getLayerRegistryStatusBadRequest(LayerRegistryV2RequestDto body) {
        return step("Проверяем, что реестр слоев v2 вернул 400 BAD_REQUEST", () -> getLayerRegistry(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }
}

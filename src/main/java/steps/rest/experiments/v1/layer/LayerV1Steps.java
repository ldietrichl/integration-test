package steps.rest.experiments.v1.layer;

import constants.Endpoints;
import dto.experiments.layers.LayerGetChangeRequestDto;
import dto.experiments.layers.LayerRegistryGetRequestDto;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

public class LayerV1Steps {

    private final RestClient client;

    public LayerV1Steps(RestClient restClient) {
        this.client = restClient;
    }

    public ValidatableResponseWrapper createOrChangeLayer(LayerGetChangeRequestDto body) {
        return client.post(spec -> spec.body(body), Endpoints.LayersV1.V1_LAYERS);
    }

    public ValidatableResponseWrapper getLayerById(Long id) {
        return client.get(spec -> spec.pathParam("id", id), Endpoints.LayersV1.V1_LAYERS_ID);
    }

    public ValidatableResponseWrapper deleteLayerById(Long id) {
        return client.delete(spec -> spec.pathParam("id", id), Endpoints.LayersV1.V1_LAYERS_ID);
    }

    public ValidatableResponseWrapper getLayerRegistry(LayerRegistryGetRequestDto body) {
        return client.post(spec -> spec.body(body), Endpoints.LayersV1.V1_LAYERS_REGISTRY);
    }
}

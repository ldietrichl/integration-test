package steps.rest.configurations.v2;

import constants.Endpoints;
import dto.configurations.v2.ConfigurationsV2ActionPostRequestDto;
import dto.configurations.v2.ConfigurationsV2GenerateSplittingConfigPostRequestDto;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

import static constants.Endpoints.Configurations.V2_CONF_GENERATE_SPLITTING_CONFIG;

public class ConfigurationsSteps {
    private final RestClient client;

    public ConfigurationsSteps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper postConfigActionRequest(List<ConfigurationsV2ActionPostRequestDto> body) {
        return client.post(spec -> spec.body(body), Endpoints.Configurations.V2_CONF_ACTION_REQUEST);
    }

    public ValidatableResponseWrapper postConfigActionRequest(String body) {
        return client.post(spec -> spec.body(body), Endpoints.Configurations.V2_CONF_ACTION_REQUEST);
    }

    public ValidatableResponseWrapper postGenerateSplittingConfig(ConfigurationsV2GenerateSplittingConfigPostRequestDto body) {
        return client.post(spec -> spec.body(body), V2_CONF_GENERATE_SPLITTING_CONFIG);
    }
}

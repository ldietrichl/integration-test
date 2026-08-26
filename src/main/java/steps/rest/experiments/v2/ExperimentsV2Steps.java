package steps.rest.experiments.v2;

import constants.Endpoints;
import dto.experiments.v2.ExperimentV2PostRequestDto;
import dto.experiments.v2.ExperimentV2PostRequestDtoBuilder;
import dto.experiments.v2.statuschange.CompleteActionRequestDto;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import steps.rest.experiments.v2.registry.RegistryV2Steps;

import java.util.List;

import static io.perfeccionista.framework.datasource.Stash.stash;
import static io.qameta.allure.Allure.step;

import ru.sber.qa.validation.ValidatableJson;

import static dto.experiments.v2.ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto;

public class ExperimentsV2Steps {
    private final RestClient client;

    public ExperimentsV2Steps(RestClient client) {
        this.client = client;
    }

    public RegistryV2Steps experimentsV2RegistrySteps() {
        return new RegistryV2Steps(client);
    }

    public ValidatableResponseWrapper createOrChangeExperimentV2(ExperimentV2PostRequestDto body) {
        ValidatableResponseWrapper responseWrapper = step("Создание эксперимента V2", () ->
                client.post(spec ->
                                spec.body(ExperimentV2PostRequestDto.toJson(body))
                        , Endpoints.ExperimentsV2.V2_EXPERIMENTS));

        stash()
                .put("experimentV2Id", responseWrapper.toJsonPath().getString("id"))
                .put("experimentV2Name", responseWrapper.toJsonPath().getString("name"))
                .put("experimentV2HypothesisDesc", responseWrapper.toJsonPath().getString("hypothesisDesc"))
                .put(
                        "experimentV2ExperimentsGroup0"
                        , responseWrapper.toJsonPath().getString("experimentsGroup[0].name"))
                .put("experimentV2Salt", responseWrapper.toJsonPath().getString("salt"))
                .put("experimentV2Creator", responseWrapper.toJsonPath().getString("creator"));

        return responseWrapper;
    }

    public ValidatableResponseWrapper createOrChangeExperimentV2() {
        return createOrChangeExperimentV2(ExperimentV2PostRequestDtoBuilder.buildDefaultExperimentV2PostRequestDto());
    }

    public ValidatableResponseWrapper getExperimentV2ById(Long id) {
        return client.get(spec -> spec.pathParam("id", id), Endpoints.ExperimentsV2.V2_EXPERIMENTS_ID);
    }

    public ValidatableResponseWrapper deleteExperimentV2ById(Long id) {
        return client.delete(spec -> spec.pathParam("id", id), Endpoints.ExperimentsV2.V2_EXPERIMENTS_ID);
    }

    public ValidatableResponseWrapper completeStatusChangeAction(List<CompleteActionRequestDto> body) {
        return client.post(
                spec -> spec.body(body),
                Endpoints.ExperimentsV2.V2_EXPERIMENTS_COMPLETE_ACTION
        );
    }

    public ValidatableResponseWrapper completeStatusChangeAction(String body) {
        return client.post(
                spec -> spec.body(body),
                Endpoints.ExperimentsV2.V2_EXPERIMENTS_COMPLETE_ACTION
        );
    }

    public Long createDefaultExperimentV2() {
        ValidatableJson result = createOrChangeExperimentV2(buildDefaultExperimentV2PostRequestDto())
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)).toValidatableJson();
        return result.getJsonPath().getLong(("id"));
    }
}

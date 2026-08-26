package ru.sber.qa.experiments.EXPLAB_2539;

import dto.experiments.layers.LayerGetChangeRequestDto;
import flow.Flows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import request.explab2539.layers.LayerV2ByIdTestDataFactory;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.ArrayList;
import java.util.List;

import static io.qameta.allure.Allure.step;

public abstract class AbstractLayerV2ByIdFlowTest extends Flows {

    private final List<Long> createdLayerIds = new ArrayList<>();

    @AfterEach
    void cleanupLayers() {
        if (createdLayerIds.isEmpty()) {
            return;
        }
        step("Удаляем тестовые слои EXPLAB-2539", () -> createdLayerIds.forEach(id -> {
            try {
                getFlowWithRest().flow().restCustomSteps().layerV2Steps().deleteLayerById(id);
            } catch (RuntimeException firstDeleteError) {
                try {
                    getFlowWithRest().flow().restCustomSteps().layerV2Steps().stopLayerById(id);
                    getFlowWithRest().flow().restCustomSteps().layerV2Steps().deleteLayerById(id);
                } catch (RuntimeException ignored) {
                    // cleanup не должен маскировать основную ошибку теста
                }
            }
        }));
        createdLayerIds.clear();
    }

    protected Long createLayer(FlowWithRest flow, LayerV2ByIdTestDataFactory.LayerFixture fixture) {
        LayerGetChangeRequestDto request = LayerV2ByIdTestDataFactory.layerRequest(fixture);
        ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps()
                .createOrChangeLayer(request)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
        Long layerId = response.toJsonPath().getLong("id");
        createdLayerIds.add(layerId);
        return layerId;
    }
}

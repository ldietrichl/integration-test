package ru.sber.qa.experiments.EXPLAB_2434;

import dto.experiments.layers.LayerGetChangeRequestDto;
import flow.Flows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import request.explab2434.layers.LayerRegistryV2TestDataFactory;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.ArrayList;
import java.util.List;

import static io.qameta.allure.Allure.step;

public abstract class AbstractLayerRegistryV2FlowTest extends Flows {

    private final List<Long> createdLayerIds = new ArrayList<>();

    @AfterEach
    void cleanupLayers() {
        if (createdLayerIds.isEmpty()) {
            return;
        }
        step("Удаляем тестовые слои EXPLAB-2434", () -> createdLayerIds.forEach(id -> {
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

    protected Long createLayer(FlowWithRest flow, LayerRegistryV2TestDataFactory.LayerFixture fixture) {
        LayerGetChangeRequestDto request = LayerRegistryV2TestDataFactory.layerRequest(fixture);
        ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps()
                .createOrChangeLayer(request)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
        Long layerId = response.toJsonPath().getLong("id");
        createdLayerIds.add(layerId);
        return layerId;
    }

    protected List<Long> createLayers(FlowWithRest flow, List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures) {
        return fixtures.stream().map(fixture -> createLayer(flow, fixture)).toList();
    }
}

package ru.sber.qa.experiments.EXPLAB_2539;

import config.environment.EnvironmentConfigWithRest;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.explab2539.layers.LayerV2ByIdTestDataFactory;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static util.explab2539.LayerV2ByIdAssertions.shouldHaveSplittingPointObject;
import static util.explab2539.LayerV2ByIdAssertions.shouldNotExposeDeprecatedSplittingPointScalarFields;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2539-layer-v2-by-id")
public class LayerV2GetByIdSplittingPointFlowTest extends AbstractLayerV2ByIdFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2539-LAY-01. GET /api/v2/experiments/layers/{id} для MAPPER возвращает splittingPoint {code,name}")
    void getMapperLayerByIdShouldReturnSplittingPointObject() {
        String prefix = LayerV2ByIdTestDataFactory.uniquePrefix();
        var fixture = LayerV2ByIdTestDataFactory.mapperLayer(prefix);
        Long[] createdLayerId = new Long[1];

        getFlowWithRest()
                .step("Создаем слой MAPPER через v2 DTO", flow -> createdLayerId[0] = createLayer(flow, fixture))
                .step("Получаем слой по id через v2 и проверяем объект splittingPoint", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerById(createdLayerId[0]);
                    shouldHaveSplittingPointObject(response, createdLayerId[0], "MAPPER", "Маппер");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2539-LAY-02. GET /api/v2/experiments/layers/{id} для REACTIONS возвращает splittingPoint {code,name}")
    void getReactionsLayerByIdShouldReturnSplittingPointObject() {
        String prefix = LayerV2ByIdTestDataFactory.uniquePrefix();
        var fixture = LayerV2ByIdTestDataFactory.reactionsLayer(prefix);
        Long[] createdLayerId = new Long[1];

        getFlowWithRest()
                .step("Создаем слой REACTIONS через v2 DTO", flow -> createdLayerId[0] = createLayer(flow, fixture))
                .step("Получаем слой по id через v2 и проверяем объект splittingPoint", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerById(createdLayerId[0]);
                    shouldHaveSplittingPointObject(response, createdLayerId[0], "REACTIONS", "Модуль реакций");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2539-LAY-03. GET layer by id не отдает deprecated scalar поля splittingPointCode/splittingPointName")
    void getLayerByIdShouldNotExposeDeprecatedSplittingPointScalarFields() {
        String prefix = LayerV2ByIdTestDataFactory.uniquePrefix();
        var fixture = LayerV2ByIdTestDataFactory.mapperLayer(prefix);
        Long[] createdLayerId = new Long[1];

        getFlowWithRest()
                .step("Создаем слой MAPPER через v2 DTO", flow -> createdLayerId[0] = createLayer(flow, fixture))
                .step("Получаем слой по id через v2 и проверяем отсутствие deprecated scalar-полей", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerById(createdLayerId[0]);
                    shouldHaveSplittingPointObject(response, createdLayerId[0], "MAPPER", "Маппер");
                    shouldNotExposeDeprecatedSplittingPointScalarFields(response);
                })
                .run();
    }
}

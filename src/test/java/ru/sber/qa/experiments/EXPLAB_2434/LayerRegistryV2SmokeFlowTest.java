package ru.sber.qa.experiments.EXPLAB_2434;

import config.environment.EnvironmentConfigWithRest;
import dto.experiments.v2.layers.LayerRegistryV2RequestDto;
import dto.experiments.v2.layers.LayerRegistryV2RequestDtoBuilder;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.explab2434.layers.LayerRegistryV2TestDataFactory;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

import static util.explab2434.LayerRegistryV2Assertions.shouldContainLayer;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainOnlyNamesWithPrefix;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveRegistryEnvelope;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveShareTotalForLayer;
import static util.explab2434.LayerRegistryV2Assertions.shouldNotHaveDuplicateIds;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2SmokeFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-01. Реестр слоев v2 возвращает созданный слой и полный базовый envelope")
    void registryShouldReturnCreatedLayerWithBaseEnvelope() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        var fixture = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix).get(0);
        Long[] createdLayerId = new Long[1];

        getFlowWithRest()
                .step("Создаем слой для проверки реестра", flow -> createdLayerId[0] = createLayer(flow, fixture))
                .step("Запрашиваем реестр слоев v2 по name like тестового префикса", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                            .toBuilder()
                            .size(20)
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveRegistryEnvelope(response);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldContainLayer(response, createdLayerId[0], fixture.name());
                    shouldHaveShareTotalForLayer(response, fixture.name(), fixture.shareTotal());
                    shouldNotHaveDuplicateIds(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-02. Реестр слоев v2 возвращает обязательные поля DTO")
    void registryShouldReturnRequiredLayerDtoFields() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем несколько слоев", flow -> createLayers(flow, fixtures))
                .step("Проверяем наличие ключевых полей DTO в ответе", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                            .toBuilder()
                            .size(20)
                            .build();

                    flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request)
                            .should(
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 3"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.id != null }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.name != null }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.status.code == 'DRAFT' }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.priority != null }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.startDt != null }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.endDt != null }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.salt != null }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.shares != null && it.shares.size() > 0 }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.shareTotal != null }"),
                                    ru.sber.qa.matchers.RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.every { it.splittingPointCode == 'MAPPER' }")
                            );
                })
                .run();
    }
}

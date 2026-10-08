package ru.sber.qa.experiments.EXPLAB_2434;

import config.environment.EnvironmentConfigWithRest;
import dto.enums.OperatorCodeExpression;
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
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

import static util.explab2434.LayerRegistryV2Assertions.shouldContainAllExpectedNames;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveAllStatuses;
import static util.explab2434.LayerRegistryV2Assertions.shouldMatchSearchByNameIgnoreCase;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2FilterSearchFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-03. Фильтр name like и поиск search работают совместно")
    void registryShouldApplyNameLikeFilterAndSearchTogether() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        String betaName = prefix + "-BETA";

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Запрашиваем слой через фильтр по префиксу и search в другом регистре", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                            .toBuilder()
                            .search("beta")
                            .size(20)
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request)
                            .should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 1"));
                    shouldContainAllExpectedNames(response, List.of(betaName));
                    shouldMatchSearchByNameIgnoreCase(response, "beta");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-04. Фильтры внутри одного блока применяются как AND")
    void registryShouldApplyFiltersInsideBlockAsAnd() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        String gammaName = prefix + "-GAMMA";

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по name like + priority equal в одном блоке", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("priority", OperatorCodeExpression.EQUAL, "20")
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request)
                            .should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 1"));
                    shouldContainAllExpectedNames(response, List.of(gammaName));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-05. Несколько блоков фильтров применяются как OR")
    void registryShouldApplyFilterBlocksAsOr() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем двумя блоками: priority=10 OR priority=30", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(
                                    List.of(
                                            LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                            LayerRegistryV2RequestDtoBuilder.filter("priority", OperatorCodeExpression.EQUAL, "10")
                                    ),
                                    List.of(
                                            LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                            LayerRegistryV2RequestDtoBuilder.filter("priority", OperatorCodeExpression.EQUAL, "30")
                                    )
                            ))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request)
                            .should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 2"));
                    shouldContainAllExpectedNames(response, List.of(prefix + "-ALPHA", prefix + "-BETA"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-06. Фильтр status equal DRAFT возвращает только черновики")
    void registryShouldFilterByDraftStatus() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по статусу DRAFT и тестовому префиксу", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("status", OperatorCodeExpression.EQUAL, "DRAFT")
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request)
                            .should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 3"));
                    shouldHaveAllStatuses(response, "DRAFT");
                })
                .run();
    }
}

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
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static util.explab2434.LayerRegistryV2Assertions.shouldContainAllExpectedNames;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainOnlyIds;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainOnlyNamesWithPrefix;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveAllEndDtBetween;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveAllStartDtBetween;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveAllStatuses;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveOnlyPriorities;
import static util.explab2434.LayerRegistryV2Assertions.shouldNotContainNames;
import static util.explab2434.LayerRegistryV2Assertions.shouldNotHavePriorities;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2AdvancedFilterFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-14. Фильтр id equal возвращает только выбранный слой")
    void registryShouldFilterByIdEqual() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        AtomicReference<Long> targetLayerIdRef = new AtomicReference<>();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> {
                    List<Long> ids = createLayers(flow, fixtures);
                    targetLayerIdRef.set(ids.get(1));
                })
                .step("Фильтруем по id equal", flow -> {
                    Long targetLayerId = targetLayerIdRef.get();
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("id", OperatorCodeExpression.EQUAL, String.valueOf(targetLayerId))
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyIds(response, List.of(targetLayerId));
                    shouldContainAllExpectedNames(response, List.of(prefix + "-BETA"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-15. Фильтр name not_like исключает слой по подстроке")
    void registryShouldFilterByNameNotLike() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по префиксу и исключаем BETA через not_like", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.NOT_LIKE, "BETA")
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldContainAllExpectedNames(response, List.of(prefix + "-ALPHA", prefix + "-GAMMA"));
                    shouldNotContainNames(response, List.of(prefix + "-BETA"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-16. Фильтр status in DRAFT возвращает созданные черновики")
    void registryShouldFilterByStatusIn() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по status in [DRAFT]", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("status", OperatorCodeExpression.IN, "DRAFT")
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveAllStatuses(response, "DRAFT");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-17. Фильтр priority in возвращает только выбранные приоритеты")
    void registryShouldFilterByPriorityIn() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по priority in [10, 30]", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("priority", OperatorCodeExpression.IN, "10", "30")
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveOnlyPriorities(response, List.of(10L, 30L));
                    shouldContainAllExpectedNames(response, List.of(prefix + "-ALPHA", prefix + "-BETA"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-18. Фильтр priority not_in исключает выбранный приоритет")
    void registryShouldFilterByPriorityNotIn() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по priority not_in [20]", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("priority", OperatorCodeExpression.NOT_IN, "20")
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldNotHavePriorities(response, List.of(20L));
                    shouldNotContainNames(response, List.of(prefix + "-GAMMA"));
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-19. Фильтр startDt more_equal/less_equal работает как диапазон")
    void registryShouldFilterByStartDtRange() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        long from = fixtures.get(1).startDt();
        long to = fixtures.get(2).startDt();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по диапазону startDt", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("startDt", OperatorCodeExpression.MORE_EQUAL, String.valueOf(from)),
                                    LayerRegistryV2RequestDtoBuilder.filter("startDt", OperatorCodeExpression.LESS_EQUAL, String.valueOf(to))
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveAllStartDtBetween(response, from, to);
                    shouldContainAllExpectedNames(response, List.of(prefix + "-BETA", prefix + "-GAMMA"));
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-20. Фильтр endDt more_equal/less_equal работает как диапазон")
    void registryShouldFilterByEndDtRange() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        long from = fixtures.get(0).endDt();
        long to = fixtures.get(1).endDt();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по диапазону endDt", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("endDt", OperatorCodeExpression.MORE_EQUAL, String.valueOf(from)),
                                    LayerRegistryV2RequestDtoBuilder.filter("endDt", OperatorCodeExpression.LESS_EQUAL, String.valueOf(to))
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveAllEndDtBetween(response, from, to);
                    shouldContainAllExpectedNames(response, List.of(prefix + "-ALPHA", prefix + "-BETA"));
                })
                .run();
    }
}

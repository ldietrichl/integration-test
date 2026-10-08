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
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveAllEndDtEqualTo;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveAllStartDtEqualTo;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveContentSize;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveOnlyPriorities;
import static util.explab2434.LayerRegistryV2Assertions.shouldNotContainNames;
import static util.explab2434.LayerRegistryV2Assertions.shouldNotHaveIds;
import static util.explab2434.LayerRegistryV2Assertions.shouldNotHavePriorities;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2OperatorCoverageFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-28. Фильтр name equal возвращает точное совпадение")
    void registryShouldFilterByNameEqual() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        String targetName = prefix + "-BETA";

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по name equal", flow -> {
                    LayerRegistryV2RequestDto request = requestWithSingleFilter("name", OperatorCodeExpression.EQUAL, targetName);

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveContentSize(response, 1);
                    shouldContainAllExpectedNames(response, List.of(targetName));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-29. Фильтр name not_equal исключает точное совпадение")
    void registryShouldFilterByNameNotEqual() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        String excludedName = prefix + "-BETA";

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по name like prefix + name not_equal BETA", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.NOT_EQUAL, excludedName)
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveContentSize(response, 2);
                    shouldNotContainNames(response, List.of(excludedName));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-30. Фильтр name in возвращает выбранный набор имен")
    void registryShouldFilterByNameIn() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        List<String> expectedNames = List.of(prefix + "-ALPHA", prefix + "-GAMMA");

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по name in [ALPHA, GAMMA]", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.IN.getValue(), expectedNames)
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveContentSize(response, 2);
                    shouldContainAllExpectedNames(response, expectedNames);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-31. Фильтр name like_any ищет по нескольким подстрокам")
    void registryShouldFilterByNameLikeAny() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по prefix + name like_any [ALPHA, GAMMA]", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE_ANY.getValue(), List.of("ALPHA", "GAMMA"))
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveContentSize(response, 2);
                    shouldContainAllExpectedNames(response, List.of(prefix + "-ALPHA", prefix + "-GAMMA"));
                    shouldNotContainNames(response, List.of(prefix + "-BETA"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-32. Фильтр id in/not_in работает для набора id")
    void registryShouldFilterByIdInAndNotIn() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        AtomicReference<List<Long>> idsRef = new AtomicReference<>();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> idsRef.set(createLayers(flow, fixtures)))
                .step("Фильтруем по id in [ALPHA, GAMMA]", flow -> {
                    List<Long> ids = idsRef.get();
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("id", OperatorCodeExpression.IN.getValue(),
                                            List.of(String.valueOf(ids.get(0)), String.valueOf(ids.get(2))))
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyIds(response, List.of(ids.get(0), ids.get(2)));
                    shouldHaveContentSize(response, 2);
                })
                .step("Фильтруем по prefix + id not_in [BETA]", flow -> {
                    List<Long> ids = idsRef.get();
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("id", OperatorCodeExpression.NOT_IN.getValue(), List.of(String.valueOf(ids.get(1))))
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldNotHaveIds(response, List.of(ids.get(1)));
                    shouldHaveContentSize(response, 2);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-33. Фильтр priority not_equal исключает выбранный приоритет")
    void registryShouldFilterByPriorityNotEqual() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по prefix + priority not_equal 20", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("priority", OperatorCodeExpression.NOT_EQUAL, "20")
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldNotHavePriorities(response, List.of(20L));
                    shouldHaveOnlyPriorities(response, List.of(10L, 30L));
                    shouldHaveContentSize(response, 2);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-34. Фильтры startDt/endDt equal возвращают слой с точной датой")
    void registryShouldFilterByDateEqual() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        LayerRegistryV2TestDataFactory.LayerFixture target = fixtures.get(1);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Фильтруем по startDt equal BETA", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("startDt", OperatorCodeExpression.EQUAL, String.valueOf(target.startDt()))
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveContentSize(response, 1);
                    shouldContainAllExpectedNames(response, List.of(target.name()));
                    shouldHaveAllStartDtEqualTo(response, target.startDt());
                })
                .step("Фильтруем по endDt equal BETA", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("endDt", OperatorCodeExpression.EQUAL, String.valueOf(target.endDt()))
                            )))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveContentSize(response, 1);
                    shouldContainAllExpectedNames(response, List.of(target.name()));
                    shouldHaveAllEndDtEqualTo(response, target.endDt());
                })
                .run();
    }

    private LayerRegistryV2RequestDto requestWithSingleFilter(String paramCode, OperatorCodeExpression operatorCode, String value) {
        return LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                .toBuilder()
                .size(20)
                .filters(List.of(List.of(LayerRegistryV2RequestDtoBuilder.filter(paramCode, operatorCode, value))))
                .build();
    }
}

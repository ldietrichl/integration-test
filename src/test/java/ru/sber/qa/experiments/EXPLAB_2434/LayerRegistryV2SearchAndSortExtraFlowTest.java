package ru.sber.qa.experiments.EXPLAB_2434;

import config.environment.EnvironmentConfigWithRest;
import dto.enums.OperatorCodeExpression;
import dto.enums.SortsDirections;
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

import static util.explab2434.LayerRegistryV2Assertions.shouldBeSortedByIntegerField;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainAllExpectedNames;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainOnlyIds;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainOnlyNamesWithPrefix;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveContentSize;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveNamesInOrder;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2SearchAndSortExtraFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-39. Search по id возвращает слой с указанным id")
    void registryShouldSearchById() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        AtomicReference<Long> targetLayerIdRef = new AtomicReference<>();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> {
                    List<Long> ids = createLayers(flow, fixtures);
                    targetLayerIdRef.set(ids.get(1));
                })
                .step("Ищем по точному id слоя", flow -> {
                    Long targetLayerId = targetLayerIdRef.get();
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .search(String.valueOf(targetLayerId))
                            .splittingPointCode("MAPPER")
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveContentSize(response, 1);
                    shouldContainOnlyIds(response, List.of(targetLayerId));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-40. Search по полному имени возвращает ожидаемый слой")
    void registryShouldSearchByFullName() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        String targetName = prefix + "-GAMMA";

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Ищем по полному имени слоя", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .search(targetName)
                            .splittingPointCode("MAPPER")
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveContentSize(response, 1);
                    shouldContainAllExpectedNames(response, List.of(targetName));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-41. Явная сортировка id ASC/DESC работает независимо от дефолта")
    void registryShouldSortByIdExplicitly() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Сортируем id ASC", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(
                            requestByPrefixAndIdSort(prefix, SortsDirections.ASC));
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByIntegerField(response, "content.id", true);
                })
                .step("Сортируем id DESC", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(
                            requestByPrefixAndIdSort(prefix, SortsDirections.DESC));
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByIntegerField(response, "content.id", false);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-42. Мультисортировка priority ASC + name DESC применяет второй ключ внутри одинакового priority")
    void registryShouldApplyMultiSort() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        long start = fixturesDate("2026-04-10T00:00:00Z");
        long end = fixturesDate("2026-12-20T00:00:00Z");
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = List.of(
                new LayerRegistryV2TestDataFactory.LayerFixture(prefix + "-ALPHA", 10, start, end, 0, 1000, "MAPPER"),
                new LayerRegistryV2TestDataFactory.LayerFixture(prefix + "-BETA", 10, start, end, 1000, 2000, "MAPPER"),
                new LayerRegistryV2TestDataFactory.LayerFixture(prefix + "-GAMMA", 20, start, end, 2000, 3000, "MAPPER")
        );

        getFlowWithRest()
                .step("Создаем тестовые слои с одинаковым priority у ALPHA/BETA", flow -> createLayers(flow, fixtures))
                .step("Сортируем priority ASC + name DESC", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                            .toBuilder()
                            .size(20)
                            .sorts(List.of(
                                    LayerRegistryV2RequestDtoBuilder.sort("priority", SortsDirections.ASC),
                                    LayerRegistryV2RequestDtoBuilder.sort("name", SortsDirections.DESC)
                            ))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveNamesInOrder(response, List.of(prefix + "-BETA", prefix + "-ALPHA", prefix + "-GAMMA"));
                })
                .run();
    }

    private LayerRegistryV2RequestDto requestByPrefixAndIdSort(String prefix, SortsDirections direction) {
        return LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                .toBuilder()
                .size(20)
                .filters(List.of(List.of(LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix))))
                .sorts(List.of(LayerRegistryV2RequestDtoBuilder.sort("id", direction)))
                .build();
    }

    private long fixturesDate(String isoDateTime) {
        return java.time.Instant.parse(isoDateTime).toEpochMilli();
    }
}

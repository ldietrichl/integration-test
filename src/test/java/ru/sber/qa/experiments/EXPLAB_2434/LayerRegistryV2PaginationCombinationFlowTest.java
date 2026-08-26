package ru.sber.qa.experiments.EXPLAB_2434;

import config.environment.EnvironmentConfigWithRest;
import dto.enums.OperatorCodeExpression;
import dto.enums.SortsDirections;
import dto.experiments.v2.layers.LayerRegistryV2RequestDto;
import dto.experiments.v2.layers.LayerRegistryV2RequestDtoBuilder;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.explab2434.layers.LayerRegistryV2TestDataFactory;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static util.explab2434.LayerRegistryV2Assertions.shouldBeSortedByIntegerField;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainOnlyNamesWithPrefix;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveAllSplittingPointCode;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveContentSize;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveOnlyPriorities;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveTotalPages;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2PaginationCombinationFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-21. Search без совпадений возвращает пустой content без ошибки")
    void registryShouldReturnEmptyContentWhenSearchHasNoMatches() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Ищем заведомо отсутствующую подстроку внутри тестового набора", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                            .toBuilder()
                            .size(20)
                            .search("__not_found_explab_2434__")
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveContentSize(response, 0);
                    shouldHaveTotalPages(response, 0);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-22. Пагинация не возвращает дубли между страницами")
    void registryShouldPageResultsWithoutDuplicates() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем три тестовых слоя", flow -> createLayers(flow, fixtures))
                .step("Запрашиваем page=0 и page=1 при size=2", flow -> {
                    LayerRegistryV2RequestDto page0Request = pageRequest(prefix, 0, 2);
                    LayerRegistryV2RequestDto page1Request = pageRequest(prefix, 1, 2);

                    ValidatableResponseWrapper page0Response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(page0Request);
                    ValidatableResponseWrapper page1Response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(page1Request);

                    shouldHaveTotalPages(page0Response, 2);
                    shouldHaveTotalPages(page1Response, 2);
                    shouldHaveContentSize(page0Response, 2);
                    shouldHaveContentSize(page1Response, 1);
                    assertNoDuplicateIdsAcrossPages(page0Response, page1Response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-23. Search + filter + sort применяются совместно")
    void registryShouldApplySearchFilterAndSortTogether() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Ищем по AQA, фильтруем priority in [10,20,30] и сортируем priority DESC", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(20)
                            .search("aqa")
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                                    LayerRegistryV2RequestDtoBuilder.filter("priority", OperatorCodeExpression.IN, "10", "20", "30")
                            )))
                            .sorts(List.of(LayerRegistryV2RequestDtoBuilder.sort("priority", SortsDirections.DESC)))
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveOnlyPriorities(response, List.of(10L, 20L, 30L));
                    shouldBeSortedByIntegerField(response, "content.priority", false);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-24. splittingPointCode ограничивает выдачу точкой MAPPER")
    void registryShouldRespectSplittingPointCode() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои MAPPER", flow -> createLayers(flow, fixtures))
                .step("Запрашиваем реестр с splittingPointCode=MAPPER", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                            .toBuilder()
                            .size(20)
                            .splittingPointCode("MAPPER")
                            .build();

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveAllSplittingPointCode(response, "MAPPER");
                })
                .run();
    }

    private LayerRegistryV2RequestDto pageRequest(String prefix, int page, int size) {
        return LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                .toBuilder()
                .page(page)
                .size(size)
                .sorts(List.of(LayerRegistryV2RequestDtoBuilder.sort("id", SortsDirections.DESC)))
                .build();
    }

    private void assertNoDuplicateIdsAcrossPages(ValidatableResponseWrapper firstPage, ValidatableResponseWrapper secondPage) {
        List<Long> firstIds = numericList(firstPage, "content.id");
        List<Long> secondIds = numericList(secondPage, "content.id");
        Set<Long> intersection = new HashSet<>(firstIds);
        intersection.retainAll(secondIds);
        Assertions.assertTrue(intersection.isEmpty(),
                "Между страницами не должно быть дублей. page0=%s, page1=%s, intersection=%s"
                        .formatted(firstIds, secondIds, intersection));
    }

    private List<Long> numericList(ValidatableResponseWrapper response, String jsonPath) {
        List<Object> rawValues = response.toJsonPath().getList(jsonPath);
        return rawValues.stream()
                .map(value -> value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value)))
                .toList();
    }
}

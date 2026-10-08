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

import static util.explab2434.LayerRegistryV2Assertions.shouldBeSortedByDateStringField;
import static util.explab2434.LayerRegistryV2Assertions.shouldBeSortedByIntegerField;
import static util.explab2434.LayerRegistryV2Assertions.shouldBeSortedByStringField;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainOnlyNamesWithPrefix;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2SortFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-07. Сортировка по priority ASC/DESC")
    void registryShouldSortByPriority() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои с разными priority", flow -> createLayers(flow, fixtures))
                .step("Проверяем сортировку priority ASC", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(
                            requestWithPrefixFilterAndSort(prefix, "priority", SortsDirections.ASC));
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByIntegerField(response, "content.priority", true);
                })
                .step("Проверяем сортировку priority DESC", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(
                            requestWithPrefixFilterAndSort(prefix, "priority", SortsDirections.DESC));
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByIntegerField(response, "content.priority", false);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-08. Сортировка по name ASC/DESC")
    void registryShouldSortByName() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои с разными name", flow -> createLayers(flow, fixtures))
                .step("Проверяем сортировку name ASC", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(
                            requestWithPrefixFilterAndSort(prefix, "name", SortsDirections.ASC));
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByStringField(response, "content.name", true);
                })
                .step("Проверяем сортировку name DESC", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(
                            requestWithPrefixFilterAndSort(prefix, "name", SortsDirections.DESC));
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByStringField(response, "content.name", false);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-09. Дефолтная сортировка без sorts идет по id DESC")
    void registryShouldApplyDefaultIdDescSort() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> createLayers(flow, fixtures))
                .step("Запрашиваем без sorts и проверяем id DESC", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                            .toBuilder()
                            .size(20)
                            .build();
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByIntegerField(response, "content.id", false);
                })
                .run();
    }

    //@Disabled("EXPLAB-2434-BUG-01: /layers/registry возвращает 400 при sorts.paramCode=startDt/endDt; включить после фикса")
    @Test
    @DisplayName("EXPLAB-2434-LR-10. Сортировка по startDt/endDt ASC/DESC")
    void registryShouldSortByDates() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);

        getFlowWithRest()
                .step("Создаем тестовые слои с разными датами", flow -> createLayers(flow, fixtures))
                .step("Проверяем сортировку startDt ASC", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(
                            requestWithPrefixFilterAndSort(prefix, "startDt", SortsDirections.ASC));
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByDateStringField(response, "content.startDt", true);
                })
                .step("Проверяем сортировку endDt DESC", flow -> {
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(
                            requestWithPrefixFilterAndSort(prefix, "endDt", SortsDirections.DESC));
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldBeSortedByDateStringField(response, "content.endDt", false);
                })
                .run();
    }

    private LayerRegistryV2RequestDto requestWithPrefixFilterAndSort(String prefix, String sortParam, SortsDirections direction) {
        return LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                .toBuilder()
                .size(20)
                .filters(List.of(List.of(LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix))))
                .sorts(List.of(LayerRegistryV2RequestDtoBuilder.sort(sortParam, direction)))
                .build();
    }
}

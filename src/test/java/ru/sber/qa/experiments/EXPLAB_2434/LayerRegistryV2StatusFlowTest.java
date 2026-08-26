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

import static util.explab2434.LayerRegistryV2Assertions.shouldContainAllExpectedNames;
import static util.explab2434.LayerRegistryV2Assertions.shouldContainOnlyNamesWithPrefix;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveAllStatuses;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveContentSize;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveOnlyStatuses;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveStatusesInOrder;
import static util.explab2434.LayerRegistryV2Assertions.shouldNotHaveStatuses;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2StatusFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-35. Фильтр status equal IN_PROGRESS возвращает включенный слой")
    void registryShouldFilterByStatusEqualInProgress() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        AtomicReference<Long> inProgressLayerIdRef = new AtomicReference<>();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> {
                    List<Long> ids = createLayers(flow, fixtures);
                    inProgressLayerIdRef.set(ids.get(1));
                })
                .step("Включаем слой BETA", flow -> flow.restCustomSteps().layerV2Steps()
                        .startLayerByIdStatusOk(inProgressLayerIdRef.get()))
                .step("Фильтруем по status equal IN_PROGRESS", flow -> {
                    LayerRegistryV2RequestDto request = requestByPrefixAndStatus(prefix, OperatorCodeExpression.EQUAL, "IN_PROGRESS");

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldHaveContentSize(response, 1);
                    shouldContainAllExpectedNames(response, List.of(prefix + "-BETA"));
                    shouldHaveAllStatuses(response, "IN_PROGRESS");
                })
                .step("Возвращаем BETA в STOPPED перед cleanup", flow -> flow.restCustomSteps().layerV2Steps()
                        .stopLayerByIdStatusOk(inProgressLayerIdRef.get()))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-36. Фильтр status in DRAFT/STOPPED возвращает выбранные статусы")
    void registryShouldFilterByStatusInDraftAndStopped() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        AtomicReference<Long> stoppedLayerIdRef = new AtomicReference<>();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> {
                    List<Long> ids = createLayers(flow, fixtures);
                    stoppedLayerIdRef.set(ids.get(2));
                })
                .step("Переводим GAMMA в STOPPED", flow -> {
                    flow.restCustomSteps().layerV2Steps().startLayerByIdStatusOk(stoppedLayerIdRef.get());
                    flow.restCustomSteps().layerV2Steps().stopLayerByIdStatusOk(stoppedLayerIdRef.get());
                })
                .step("Фильтруем по status in [DRAFT, STOPPED]", flow -> {
                    LayerRegistryV2RequestDto request = requestByPrefixAndStatus(prefix, OperatorCodeExpression.IN, "DRAFT", "STOPPED");

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveContentSize(response, 3);
                    shouldHaveOnlyStatuses(response, List.of("DRAFT", "STOPPED"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-37. Фильтр status not_equal DRAFT исключает черновики")
    void registryShouldFilterByStatusNotEqual() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        AtomicReference<Long> inProgressLayerIdRef = new AtomicReference<>();
        AtomicReference<Long> stoppedLayerIdRef = new AtomicReference<>();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> {
                    List<Long> ids = createLayers(flow, fixtures);
                    inProgressLayerIdRef.set(ids.get(1));
                    stoppedLayerIdRef.set(ids.get(2));
                })
                .step("Готовим статусы IN_PROGRESS и STOPPED", flow -> {
                    flow.restCustomSteps().layerV2Steps().startLayerByIdStatusOk(inProgressLayerIdRef.get());
                    flow.restCustomSteps().layerV2Steps().startLayerByIdStatusOk(stoppedLayerIdRef.get());
                    flow.restCustomSteps().layerV2Steps().stopLayerByIdStatusOk(stoppedLayerIdRef.get());
                })
                .step("Фильтруем по status not_equal DRAFT", flow -> {
                    LayerRegistryV2RequestDto request = requestByPrefixAndStatus(prefix, OperatorCodeExpression.NOT_EQUAL, "DRAFT");

                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveContentSize(response, 2);
                    shouldNotHaveStatuses(response, List.of("DRAFT"));
                    shouldHaveOnlyStatuses(response, List.of("IN_PROGRESS", "STOPPED"));
                })
                .step("Возвращаем IN_PROGRESS слой в STOPPED перед cleanup", flow -> flow.restCustomSteps().layerV2Steps()
                        .stopLayerByIdStatusOk(inProgressLayerIdRef.get()))
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-38. Сортировка status ASC/DESC работает на разных статусах слоя")
    void registryShouldSortByStatus() {
        String prefix = LayerRegistryV2TestDataFactory.uniquePrefix();
        List<LayerRegistryV2TestDataFactory.LayerFixture> fixtures = LayerRegistryV2TestDataFactory.threeMapperLayers(prefix);
        AtomicReference<Long> inProgressLayerIdRef = new AtomicReference<>();
        AtomicReference<Long> stoppedLayerIdRef = new AtomicReference<>();

        getFlowWithRest()
                .step("Создаем тестовые слои", flow -> {
                    List<Long> ids = createLayers(flow, fixtures);
                    inProgressLayerIdRef.set(ids.get(1));
                    stoppedLayerIdRef.set(ids.get(2));
                })
                .step("Готовим статусы DRAFT, IN_PROGRESS, STOPPED", flow -> {
                    flow.restCustomSteps().layerV2Steps().startLayerByIdStatusOk(inProgressLayerIdRef.get());
                    flow.restCustomSteps().layerV2Steps().startLayerByIdStatusOk(stoppedLayerIdRef.get());
                    flow.restCustomSteps().layerV2Steps().stopLayerByIdStatusOk(stoppedLayerIdRef.get());
                })
                .step("Сортируем status ASC", flow -> {
                    LayerRegistryV2RequestDto request = requestByPrefixAndStatusSort(prefix, SortsDirections.ASC);
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveStatusesInOrder(response, List.of("DRAFT", "IN_PROGRESS", "STOPPED"));
                })
                .step("Сортируем status DESC", flow -> {
                    LayerRegistryV2RequestDto request = requestByPrefixAndStatusSort(prefix, SortsDirections.DESC);
                    ValidatableResponseWrapper response = flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusOk(request);
                    shouldContainOnlyNamesWithPrefix(response, prefix);
                    shouldHaveStatusesInOrder(response, List.of("STOPPED", "IN_PROGRESS", "DRAFT"));
                })
                .step("Возвращаем IN_PROGRESS слой в STOPPED перед cleanup", flow -> flow.restCustomSteps().layerV2Steps()
                        .stopLayerByIdStatusOk(inProgressLayerIdRef.get()))
                .run();
    }

    private LayerRegistryV2RequestDto requestByPrefixAndStatus(String prefix, OperatorCodeExpression operator, String... statuses) {
        return LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                .toBuilder()
                .size(20)
                .filters(List.of(List.of(
                        LayerRegistryV2RequestDtoBuilder.filter("name", OperatorCodeExpression.LIKE, prefix),
                        LayerRegistryV2RequestDtoBuilder.filter("status", operator.getValue(), List.of(statuses))
                )))
                .build();
    }

    private LayerRegistryV2RequestDto requestByPrefixAndStatusSort(String prefix, SortsDirections direction) {
        return LayerRegistryV2RequestDtoBuilder.buildDefaultDtoWithNameFilter(prefix)
                .toBuilder()
                .size(20)
                .sorts(List.of(LayerRegistryV2RequestDtoBuilder.sort("status", direction)))
                .build();
    }
}

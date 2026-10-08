package ru.sber.qa.experiments.EXPLAB_2434;

import config.environment.EnvironmentConfigWithRest;
import dto.enums.OperatorCodeExpression;
import dto.enums.SortsDirections;
import dto.experiments.v2.layers.LayerRegistryV2RequestDto;
import dto.experiments.v2.layers.LayerRegistryV2RequestDtoBuilder;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2NegativeContractFlowTest extends AbstractLayerRegistryV2FlowTest {

    @Test
    @DisplayName("EXPLAB-2434-LR-43. Некорректный paramCode фильтра не должен возвращать 200 OK")
    void registryShouldRejectUnknownFilterParamCode() {
        getFlowWithRest()
                .step("Отправляем фильтр с неизвестным paramCode", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("unknownField", OperatorCodeExpression.EQUAL, "value")
                            )))
                            .build();

                    assertNotOk(flow.restCustomSteps().layerV2Steps().getLayerRegistry(request).toResponse().statusCode());
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-44. Некорректный operatorCode фильтра не должен возвращать 200 OK")
    void registryShouldRejectUnknownFilterOperatorCode() {
        getFlowWithRest()
                .step("Отправляем фильтр с неизвестным operatorCode", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("priority", "unknown_operator", List.of("10"))
                            )))
                            .build();

                    assertNotOk(flow.restCustomSteps().layerV2Steps().getLayerRegistry(request).toResponse().statusCode());
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-45. Некорректный paramCode сортировки не должен возвращать 200 OK")
    void registryShouldRejectUnknownSortParamCode() {
        getFlowWithRest()
                .step("Отправляем сортировку с неизвестным paramCode", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .sorts(List.of(LayerRegistryV2RequestDtoBuilder.sort("unknownSortField", SortsDirections.ASC)))
                            .build();

                    assertNotOk(flow.restCustomSteps().layerV2Steps().getLayerRegistry(request).toResponse().statusCode());
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-46. Некорректное direction сортировки не должно возвращать 200 OK")
    void registryShouldRejectUnknownSortDirection() {
        getFlowWithRest()
                .step("Отправляем сортировку с неизвестным direction", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .sorts(List.of(LayerRegistryV2RequestDtoBuilder.sort("priority", "UNKNOWN_DIRECTION")))
                            .build();

                    assertNotOk(flow.restCustomSteps().layerV2Steps().getLayerRegistry(request).toResponse().statusCode());
                })
                .run();
    }

    private void assertNotOk(int statusCode) {
        Assertions.assertNotEquals(HttpStatus.SC_OK, statusCode,
                "Некорректный запрос реестра слоев не должен возвращать 200 OK");
    }
}

package ru.sber.qa.experiments.EXPLAB_2434;

import config.environment.EnvironmentConfigWithRest;
import dto.enums.OperatorCodeExpression;
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
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2ValidationFlowTest extends AbstractLayerRegistryV2FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-11. page < 0 отклоняется валидацией")
    void registryShouldRejectNegativePage() {
        getFlowWithRest()
                .step("Отправляем запрос реестра слоев с page=-1", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .page(-1)
                            .build();

                    flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusBadRequest(request);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2434-LR-12. size <= 0 отклоняется валидацией")
    void registryShouldRejectZeroSize() {
        getFlowWithRest()
                .step("Отправляем запрос реестра слоев с size=0", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .size(0)
                            .build();

                    flow.restCustomSteps().layerV2Steps().getLayerRegistryStatusBadRequest(request);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2434-LR-13. Некорректный тип значения фильтра не должен приводить к 200 OK")
    void registryShouldRejectIncorrectFilterValueType() {
        getFlowWithRest()
                .step("Отправляем priority equal abc", flow -> {
                    LayerRegistryV2RequestDto request = LayerRegistryV2RequestDtoBuilder.buildDefaultDto()
                            .toBuilder()
                            .filters(List.of(List.of(
                                    LayerRegistryV2RequestDtoBuilder.filter("priority", OperatorCodeExpression.EQUAL, "abc")
                            )))
                            .build();

                    int statusCode = flow.restCustomSteps().layerV2Steps().getLayerRegistry(request)
                            .toResponse().statusCode();
                    Assertions.assertNotEquals(HttpStatus.SC_OK, statusCode,
                            "Некорректный тип значения фильтра не должен возвращать 200 OK");
                })
                .run();
    }
}

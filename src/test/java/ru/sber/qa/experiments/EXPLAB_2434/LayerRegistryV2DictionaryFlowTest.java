package ru.sber.qa.experiments.EXPLAB_2434;

import config.environment.EnvironmentConfigWithRest;
import dto.dictionaries.request.ExpressionParameterDictReqDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

import static util.explab2434.LayerRegistryV2Assertions.shouldHaveRegistryLayerDictionaryParams;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveRegistryLayerOperators;
import static util.explab2434.LayerRegistryV2Assertions.shouldHaveRegistryLayerStatusEnumValues;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("explab-2434-layer-registry")
public class LayerRegistryV2DictionaryFlowTest extends AbstractLayerRegistryV2FlowTest {

    //@Disabled("EXPLAB-2434-BUG-02: /api/v1/dictionaries/expression-parameter-dict возвращает 500 для formCode=REGISTRY_LAYER")
    @Test
    @DisplayName("EXPLAB-2434-LR-25. Справочник REGISTRY_LAYER содержит параметры реестра слоев")
    void registryLayerDictionaryShouldContainLayerRegistryParams() {
        getFlowWithRest()
                .step("Получаем expression-parameter-dict для REGISTRY_LAYER", flow -> {
                    ExpressionParameterDictReqDto request = ExpressionParameterDictReqDto.builder()
                            .formCode("REGISTRY_LAYER")
                            .build();
                    ValidatableResponseWrapper response = flow.restCustomSteps().dictionariesV1Steps()
                            .getExpressionParameterDictStatusOk(request);
                    shouldHaveRegistryLayerDictionaryParams(response);
                })
                .run();
    }

    //@Disabled("EXPLAB-2434-BUG-02: /api/v1/dictionaries/expression-parameter-dict возвращает 500 для formCode=REGISTRY_LAYER")
    @Test
    @DisplayName("EXPLAB-2434-LR-26. Справочник REGISTRY_LAYER содержит операторы фильтрации")
    void registryLayerDictionaryShouldContainFilterOperators() {
        getFlowWithRest()
                .step("Получаем expression-parameter-dict для REGISTRY_LAYER и проверяем операторы", flow -> {
                    ExpressionParameterDictReqDto request = ExpressionParameterDictReqDto.builder()
                            .formCode("REGISTRY_LAYER")
                            .build();
                    ValidatableResponseWrapper response = flow.restCustomSteps().dictionariesV1Steps()
                            .getExpressionParameterDictStatusOk(request);

                    shouldHaveRegistryLayerOperators(response, "id", List.of("equal", "not_equal", "in", "not_in"));
                    shouldHaveRegistryLayerOperators(response, "name", List.of("equal", "not_equal", "in", "not_in", "like", "not_like", "like_any", "not_like_any"));
                    shouldHaveRegistryLayerOperators(response, "status", List.of("equal", "not_equal", "in", "not_in"));
                    shouldHaveRegistryLayerOperators(response, "priority", List.of("equal", "not_equal", "in", "not_in"));
                    shouldHaveRegistryLayerOperators(response, "startDt", List.of("equal", "not_equal", "in", "not_in", "more", "more_equal", "less", "less_equal"));
                    shouldHaveRegistryLayerOperators(response, "endDt", List.of("equal", "not_equal", "in", "not_in", "more", "more_equal", "less", "less_equal"));
                })
                .run();
    }

    //@Disabled("EXPLAB-2434-BUG-02: /api/v1/dictionaries/expression-parameter-dict возвращает 500 для formCode=REGISTRY_LAYER")
    @Test
    @DisplayName("EXPLAB-2434-LR-27. Справочник REGISTRY_LAYER содержит ENUM статусов слоя")
    void registryLayerDictionaryShouldContainStatusEnumValues() {
        getFlowWithRest()
                .step("Получаем expression-parameter-dict для REGISTRY_LAYER и проверяем ENUM status", flow -> {
                    ExpressionParameterDictReqDto request = ExpressionParameterDictReqDto.builder()
                            .formCode("REGISTRY_LAYER")
                            .build();
                    ValidatableResponseWrapper response = flow.restCustomSteps().dictionariesV1Steps()
                            .getExpressionParameterDictStatusOk(request);
                    shouldHaveRegistryLayerStatusEnumValues(response);
                })
                .run();
    }
}

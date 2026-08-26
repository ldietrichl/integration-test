package ru.sber.qa.experiments.layers;

import ru.sber.qa.allure.CriticalRegression;

import config.environment.EnvironmentConfigWithDbRest;
import dto.experiments.layers.LayerGetChangeRequestDto;
import dto.experiments.layers.LayerGetChangeRequestDtoBuilder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.validation.ValidatableJson;

import static io.perfeccionista.framework.datasource.Stash.stash;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithDbRest.class)
public class CreateGetChangeDeleteLayerTest extends Flows {

    @CriticalRegression
    @Test
    @DisplayName("Happy path: создание, изменение, получение и удаление слоя")
    void createGetChangeDeleteLayerSuccessTest() {

        getFlowWithDbRest()
                .step("Создание слоя", flow -> {
                    ValidatableJson result =
                            flow.restCustomSteps().layerSteps().createOrChangeLayer(LayerGetChangeRequestDtoBuilder.buildDefaultDto())
                                    .should(
                                            RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                    ).toValidatableJson();
                    stash().put("layerId", result.getJsonPath().getLong("id"));
                })

                .step("Проверка, что слой создался в БД", flow ->
                        flow.dbExpLabClient()
                                .executeSelect("""
                                        SELECT * FROM experiments.layer WHERE id = %s
                                        
                                        """.formatted(stash().get("layerId")))
                                .should(DatabaseMatchers.tableHaveSize(1)))

                .step("Изменение слоя", flow -> {
                    LayerGetChangeRequestDto body = LayerGetChangeRequestDtoBuilder.buildDefaultDto().toBuilder()
                            .id(stash().get("layerId", Long.class))
                            .description("New description")
                            .build();

                    flow.restCustomSteps().layerSteps()
                            .createOrChangeLayer(body)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                    RestMatchers.haveBodyJsonValue("description", TextConditions.equalToText("New description"))
                            ).toValidatableJson();
                })

                .step("Получение слоя", flow ->
                        flow.restCustomSteps().layerSteps()
                                .getLayerById(stash().get("layerId", Long.class))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                ))

                .step("Удаление слоя", flow ->
                        flow.restCustomSteps().layerSteps()
                                .deleteLayerById(stash().get("layerId", Long.class))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                ))

                .step("Проверка, что слой удалился в БД", flow ->
                        flow.dbExpLabClient()
                                .executeSelect("""
                                        SELECT * FROM experiments.layer WHERE id = %s
                                        
                                        """.formatted(stash().get("layerId")))
                                .should(DatabaseMatchers.tableHaveSize(0)))
                .run();
    }
}

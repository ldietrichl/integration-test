package ru.sber.qa.experiments;

import config.environment.EnvironmentConfigWithRest;
import constants.Endpoints;
import dto.experiments.v1.ExperimentsV1PostRequestDto;
import dto.experiments.v1.ExperimentsV1PostRequestDtoBuilder;
import feeders.ExperimentsFeeder;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

/**
 * Полный пример тест-класса.
 * - Формирование тела запроса через Params + Factory (без шаблонного JSON и replace)
 * - Создание эксперимента
 * - SharedState — реальный класс/хранилище id между тестами
 * - ExperimentsFeeder.* — генераторы значений
 */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExperimentApiTest extends Flows {
    /**
     * Общее хранилище для id между тестами.
     */
    public static class SharedState {
        public static Long experimentId;
    }

    @CriticalRegression
    @Test
    @Order(10)
    @DisplayName("Тест создания эксперимента")
    void testCreateExperiment() {
        getFlowWithRest().step(
                "Запрос на создание"
                , flow -> {
                    ValidatableResponseWrapper response =
                            flow.restCustomSteps().experimentsV1Steps().createDefaultExperiment();

                    // ==== сохраняем id для следующего теста ====
                    SharedState.experimentId = response.toJsonPath().getLong("id");
                    Assertions.assertNotNull(SharedState.experimentId, "experimentId не вернулся от сервиса");
                }).run();
    }

    @CriticalRegression
    @Test
    @Order(20)
    @DisplayName("Тест поиска эксперимента по id")
    void testGetExperimentById(RestService restService) {
        if (SharedState.experimentId == null) {
            throw new IllegalStateException("experimentId не установлен");
        }

        restService.restClient()
                .get(spec -> spec
                        , Endpoints.ExperimentsV1.V1_EXPERIMENTS_ID.formatted(SharedState.experimentId))
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }

    @CriticalRegression
    @Test
    @Order(30)
    @DisplayName("Тест поиска эксперимента по id")
    void testChangeStatusExperimentById(RestService restService) {
        if (SharedState.experimentId == null) {
            throw new IllegalStateException("experimentId не установлен");
        }

        String body = "{\n" +
                "  \"status\": \"IN_PROGRESS\",\n" +
                "  \"comment\": \"Сменить шаблон по продукту\",\n" +
                "  \"slave\": true,\n" +
                "  \"ignoreWarnings\": true,\n" +
                "  \"startCampaigns\": true\n" +
                "}";

        restService.restClient()
                .put(spec -> spec
                                .body(body)
                        , Endpoints.ExperimentsV1.V1_EXPERIMENTS_ID_STATUS.formatted(SharedState.experimentId))
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }

    @CriticalRegression
    @Test
    @Order(40)
    @DisplayName("Тест удаления эксперимента по id")
    void testDeleteExperimentById(RestService restService) {
        if (SharedState.experimentId == null) {
            throw new IllegalStateException("experimentId не установлен");
        }

        restService.restClient()
                .delete(spec -> spec
                        , Endpoints.ExperimentsV1.V1_EXPERIMENTS_ID.formatted(SharedState.experimentId))
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }

    @Test
    @Order(50)
    @Disabled
    @DisplayName("Создание эксперимента с двумя группами (A — целевая, B — контроль)")
    void createTwoGroupsExperiment(RestService restService) {
        // дефолты групп уже выставлены: A(size=2000, baseline=false), B(size=2000, baseline=true)
        ExperimentsV1PostRequestDto body = ExperimentsV1PostRequestDtoBuilder.buildDtoDefaultWithCustomParams(
                        ExperimentsFeeder.generateAqaMalilId()
                        , ExperimentsFeeder.startDt
                        , ExperimentsFeeder.endDt
                        , ExperimentsFeeder.generateSalt()
                        , "Эксперимент создан в рамках автокейса, с двумя группами"
                        , "АБ тестовый"
                        , List.of("103081")
                )
                // при необходимости — пробросить createdBy / hypothesisDesc / creator:
                .toBuilder()
//                .createdBy(1026L)
                .build();

        // ==== запрос на создание ====
        var response = restService.restClient()
                .post(spec -> spec
                                .body(ExperimentsV1PostRequestDto.toJson(body))
                        , Endpoints.ExperimentsV1.V1_EXPERIMENTS)
                .should(
                        haveStatusCode(HttpStatus.SC_OK));

        // ==== сохраняем id для следующего теста ====
        SharedState.experimentId = response.toJsonPath().getLong("id");
        Assertions.assertNotNull(SharedState.experimentId, "experimentId не вернулся от сервиса");
    }
}

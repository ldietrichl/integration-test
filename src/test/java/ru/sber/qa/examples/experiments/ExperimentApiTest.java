package ru.sber.qa.examples.experiments;


import dto.experiment.ExperimentRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import request.experiment.CreateExperimentParams;
import request.experiment.CreateExperimentRequestFactory;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.feeders.ExperimentsFeeder;

import java.util.List;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

/**
 * Полный пример тест-класса.
 * - Формирование тела запроса через Params + Factory (без шаблонного JSON и replace)
 * - Создание эксперимента
 *  - url                     —  URL твоего сервиса
 *  - P12_CONFIG              — на твою RestAssured-конфигурацию
 *  - SharedState             — реальный класс/хранилище id между тестами
 *  - ExperimentsFeeder.*     —  генераторы значений
 */

@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class ExperimentApiTest {


    String urlIFT = "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru";
     String urlDEV= "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru";
    String url= urlDEV;

    private final CreateExperimentRequestFactory factory = new CreateExperimentRequestFactory();


    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                    .keyStore("src/test/resources/keystore.p12", "V_at_platform_2025")
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );

    /** Общее хранилище для id между тестами. */
    public static class SharedState {
        public static Long experimentId;
    }

    @Test
    @Order(10)
    @DisplayName("Тест создания эксперимента")
    void testCreateExperiment(ru.sber.qa.services.rest.RestService restService) {
        // --- формируем параметры ---
        CreateExperimentParams params = CreateExperimentParams.builder()
                .name(ExperimentsFeeder.generateAqaMalilId())
                .salt(ExperimentsFeeder.generateSalt())
                .startDt(ExperimentsFeeder.startDt)
                .endDt(ExperimentsFeeder.endDt) // +1 день
                .cjIds(java.util.List.of("103081"))
                .hypothesisDesc("Эксперимент создан в рамках автокейса")
                .creator("Абтестовый")
                .build();

        // --- собираем DTO и JSON ---
        ExperimentRequestDto dto = factory.buildDto(params);
        String body = factory.toJson(dto);


        // ==== запрос на создание ====
        var response = restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .header("Content-Type", "application/json")
                                .accept("*/*")
                                .body(body),
                        url + "/api/v1/experiments")
                .should(
                    haveStatusCode(HttpStatus.SC_OK));

        // ==== сохраняем id для следующего теста ====
        Long experimentId = response.toJsonPath().getLong("id");
        SharedState.experimentId = experimentId;
        Assertions.assertNotNull(SharedState.experimentId, "experimentId не вернулся от сервиса");
    }

    @Test
    @Order(20)
    @DisplayName("Тест поиска эксперимента по id")
    void testGetExperimentById(ru.sber.qa.services.rest.RestService restService) {
        if (SharedState.experimentId == null) {
            throw new IllegalStateException("experimentId не установлен");
        }

        restService.restClient()
                .get(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*"),
                        url + "/api/v1/experiments/" + SharedState.experimentId)
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }

    @Test
    @Order(30)
    @DisplayName("Тест поиска эксперимента по id")
    void testChangeStatusExperimentById(ru.sber.qa.services.rest.RestService restService) {
        if (SharedState.experimentId == null) {
            throw new IllegalStateException("experimentId не установлен");
        }

        String body = "{\n" +
                "  \"status\": \"IN_PROGRESS\",\n" +
                "  \"comment\": \"Сменить шаблон по продукту\",\n" +
                "  \"slave\": true,\n" +
                "  \"ignoreWarnings\": true,\n" +
                "  \"startCampaigns\": true\n" +
                "}" ;

        restService.restClient()
                .put(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*")
                                .body(body),
                        url + "/api/v1/experiments/"+SharedState.experimentId+"/status")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }

    @Test
    @Order(40)
    @DisplayName("Тест удаления эксперимента по id")
    void testDeleteExperimentById(ru.sber.qa.services.rest.RestService restService) {
        if (SharedState.experimentId == null) {
            throw new IllegalStateException("experimentId не установлен");
        }

        restService.restClient()
                .delete(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*"),
                        url + "/api/v1/experiments/" + SharedState.experimentId)
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }

    @Test
    @Order(50)
    @Disabled
    @DisplayName("Создание эксперимента с двумя группами (A — целевая, B — контроль)")
    void createTwoGroupsExperiment(ru.sber.qa.services.rest.RestService restService) {
        // == твои «переменные из теста» ==
        String name  =  ExperimentsFeeder.generateAqaMalilId();           // как в успешном примере
        String salt  = ExperimentsFeeder.generateSalt();
        long startDt = ExperimentsFeeder.startDt;
        long endDt   = ExperimentsFeeder.endDt;

        // == параметры для фабрики ==
        CreateExperimentParams params = CreateExperimentParams.builder()
                .name(name)
                .salt(salt)
                .startDt(startDt)
                .endDt(endDt)
                .cjIds(List.of("103081"))
                // при необходимости — пробросить createdBy / hypothesisDesc / creator:
                // .createdBy(1026L)
                 .hypothesisDesc("Эксперимент создан в рамках автокейса, с двумя группами")
                // .creator("АСтеповой")
                // дефолты групп уже выставлены: A(size=2000, baseline=false), B(size=2000, baseline=true)
                .build();

        // == сборка JSON ==
        ExperimentRequestDto dto = factory.buildDto(params);
        String body = factory.toJson(dto);


        // ==== запрос на создание ====
        var response = restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .header("Content-Type", "application/json")
                                .accept("*/*")
                                .body(body),
                        url + "/api/v1/experiments")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));

        // ==== сохраняем id для следующего теста ====
        SharedState.experimentId = response.toJsonPath().getLong("id");
        Assertions.assertNotNull(SharedState.experimentId, "experimentId не вернулся от сервиса");
    }


}

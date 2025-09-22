package ru.sber.qa.examples.experiments;


import dto.ExperimentRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import request.CreateExperimentParams;
import request.CreateExperimentRequestFactory;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.feeders.ExperimentsFeeder;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

/**
 * Полный пример тест-класса.
 * - Формирование тела запроса через Params + Factory (без шаблонного JSON и replace)
 * - Создание эксперимента -> сохранение id -> получение по id
 *
 * ЗАМЕНИ:
 *  - url                     — на базовый URL твоего сервиса
 *  - P12_CONFIG              — на твою RestAssured-конфигурацию
 *  - SharedState             — на твой реальный класс/хранилище id между тестами
 *  - ExperimentsFeeder.*     — на твои генераторы значений
 */

@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class ExperimentApiTest {


    String urlIFT = "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru";
    String urlDEV= "https://ingress-v2.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru";
    String url= urlIFT;

    private final CreateExperimentRequestFactory factory = new CreateExperimentRequestFactory();


    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                    .keyStore("src/test/resources/keystore.p12", "V_at_platform_2025")
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );

    /** Общее хранилище для id между тестами (замени на свой существующий класс/поле). */
    public static class SharedState {
        public static Long experimentId;
    }

    @Test
    @Order(10)
    @DisplayName("Тест создания эксперимента")
    void testCreateExperiment(ru.sber.qa.services.rest.RestService restService) {
        // --- формируем параметры ---
        CreateExperimentParams params = CreateExperimentParams.builder()
                .name(ru.sber.qa.feeders.ExperimentsFeeder.generateAqaMalilId())
                .salt(ru.sber.qa.feeders.ExperimentsFeeder.generateSalt())
                .startDt( ru.sber.qa.feeders.ExperimentsFeeder.startDt)
                .endDt(ru.sber.qa.feeders.ExperimentsFeeder.endDt) // +1 день
                .cjIds(java.util.List.of("103081"))
                .hypothesisDesc("Du27_01/3")
                .creator("Абтестовый")
                .build();

        // --- собираем DTO и JSON ---
        ExperimentRequestDto dto = factory.buildDto(params);
        String body = factory.toJson(dto);

        System.out.println("===== REQUEST BODY =====");
        System.out.println(body);

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
        System.out.println("НОВЫЙ ID: " + experimentId);
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

    /* ===== Заглушки для примера: замени своими реализациями ===== */

    /** Твой сервис-обёртка над RestAssured. */
    public interface RestService {
        RestClient restClient();
    }

    /** Клиент с лямбда-конфигурацией, соответствующей твоим методам .post/.get/.should() */
    public interface RestClient {
        ResponseWrapper post(java.util.function.Function<RequestSpec, RequestSpec> config, String url);
        ResponseWrapper get (java.util.function.Function<RequestSpec, RequestSpec> config, String url);
    }

    /** Обертка спецификации запроса — просто тип для демонстрации. */
    public interface RequestSpec {
        RequestSpec config(Object cfg);
        RequestSpec contentType(ContentType ct);
        RequestSpec header(String name, String value);
        RequestSpec accept(String value);
        RequestSpec body(String body);
    }

    /** Обертка ответа — должна совпадать с тем, что у тебя уже есть. */
    public interface ResponseWrapper {
        ResponseWrapper should();
        ResponseWrapper haveStatusCode(int code);
        io.restassured.path.json.JsonPath toJsonPath();
    }

    /** Заглушка генератора переменных — замени на твой ExperimentsFeeder. */
    public static class ExperimentsFeeder {
        public static String generateAqaMailId() { return "aqa-" + System.currentTimeMillis(); }
        public static String generateSalt() { return "salt-" + System.nanoTime(); }
        public static long startDt() { return System.currentTimeMillis() + 60_000; }
        public static long endDt() { return System.currentTimeMillis() + 86_400_000; }
    }
}

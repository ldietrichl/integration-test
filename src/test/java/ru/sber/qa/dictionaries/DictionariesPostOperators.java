package ru.sber.qa.dictionaries;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import ru.sber.qa.allure.CriticalRegression;

import config.environment.EnvironmentConfigurationExample;
import dto.dictionaries.request.OperatorsReqDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Allure;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import net.javacrumbs.jsonunit.JsonAssert;
import org.apache.http.HttpStatus;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import request.dictionaries.DictionariesParams;
import request.dictionaries.DictionariesRequestFactory;
import ru.sber.qa.services.db.DatabaseService;
import ru.sber.qa.services.rest.RestService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;


@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class DictionariesPostOperators {
    private static final String dictionariesBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.DICTIONARIES);
    private final DictionariesRequestFactory factory = new DictionariesRequestFactory();

    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                    .keyStore("src/test/resources/keystore.p12", TEST_CONFIG.keystorePass())
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );

    /**
     * Общее хранилище для id между тестами.
     */
    public static class SharedState {
        public static Long experimentId;
    }


    @CriticalRegression
    @Test
    @DisplayName("Получить данные справочника операторов")
    void testDictionariesPostOperators(RestService restService) {
        // 1. Формируем параметры
        DictionariesParams params = DictionariesParams.builder()
                .operatorCodes(List.of("in", "more_equal"))
                .build();

        // 2. Строим DTO через фабрику
        OperatorsReqDto dto = factory.buildOperatorsDto(params);

        restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*")
                                .body(dto),
                        dictionariesBaseUri + "/api/v2/dictionaries/operators")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));

    }


    @CriticalRegression
    @Test
    @DisplayName("Получить данные справочника операторов (некорректный запрос)")
    void testDictionariesPostOperatorsInvalidBody(RestService restService) {
        String dto = "1,2,3";

        var response = restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*")
                                .body(dto),
                        dictionariesBaseUri + "/api/v2/dictionaries/operators")
                .should(
                        haveStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR));


        Allure.step("Проверить, что ответ содержит сообщение об ошибке", () -> {
            Assertions.assertEquals(
                    "Непредвиденная ошибка",
                            response.toResponse().jsonPath().getString("message")
            );
        });

    }


    @CriticalRegression
    @Test
    @DisplayName("Получить данные справочника операторов (пустой запрос)")
    void testDictionariesPostOperatorsNullBodyRequest(RestService restService) {
        // 1. Формируем параметры
        DictionariesParams params = DictionariesParams.builder()
                .operatorCodes(List.of())
                .build();

        // 2. Строим DTO через фабрику
        OperatorsReqDto dto = factory.buildOperatorsDto(params);

        var response = restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*")
                                .body(dto),
                        dictionariesBaseUri + "/api/v2/dictionaries/operators")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));

        Allure.step("Проверить, что ответ совпадает с ожидаемым JSON", () -> {
            JsonAssert.assertJsonEquals(
                    new String(Files.readAllBytes(Paths.get("src/test/resources/dictionaries/postOperatorsResponse.json"))),
                    response.toResponse().asString()
            );
        });

    }


    @Test
    @Disabled
    @DisplayName("Получить справочник точек сплиттования")
    void testChangeStatusExperimentById1(DatabaseService dbService) {
        var x = dbService.dataBaseClient("dev_explab").executeSelect("select code from dictionaries.operator_dict od ").firstRow().toSimpleRow();
        System.out.println(x);

        Stream.of(x = dbService.dataBaseClient("dev_explab").executeSelect("select code from dictionaries.operator_dict od ").firstRow().toSimpleRow());
    }
}

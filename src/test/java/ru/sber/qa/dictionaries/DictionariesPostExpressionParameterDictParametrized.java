package ru.sber.qa.dictionaries;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import ru.sber.qa.allure.CriticalRegression;

import config.environment.EnvironmentConfigurationExample;
import dto.dictionaries.request.ExpressionParameterDictReqDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import request.dictionaries.DictionariesParams;
import request.dictionaries.DictionariesRequestFactory;
import ru.sber.qa.services.rest.RestService;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;


@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class DictionariesPostExpressionParameterDictParametrized {
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
    @ParameterizedTest
    @CsvSource({"MAPPER_OBJECT_SELECT","MAPPER_REGISTRY","OPTIMIZER_REGISTRY","OPTIMIZER_OBJECT_SELECT"})
    @DisplayName("Получить справочник параметров выражений  по коду")
    void testDictionariesPostExpressionParameterAllFormCode(String code, RestService restService) {


        // 1. Формируем параметры
        DictionariesParams params = DictionariesParams.builder()
                .formCode(code) // можно оставить дефолт
                .build();

        // 2. Строим DTO через фабрику
        ExpressionParameterDictReqDto dto = factory.buildDto(params);

        var response = restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*")
                                .body(dto),
                        dictionariesBaseUri + "/api/v2/dictionaries/expression-parameter-dict")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));
    }
}

package ru.sber.qa.examples.dictionaries;


import dto.dictionaries.request.SplittingPointTemplateReqDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import request.dictionaries.DictionariesParams;
import request.dictionaries.DictionariesRequestFactory;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.services.rest.RestService;

import java.util.List;
import java.util.stream.Stream;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class DictionariesPostSplittingPointTemplates {


    String urlIFT = "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru";
     String urlDEV= "https://ingress-v2.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru";
    String url= urlDEV;

    private final DictionariesRequestFactory factory = new DictionariesRequestFactory();


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


    // список кодов, как в таблице
    static Stream<Arguments> splittingPointCodes() {
        return Stream.of(
                Arguments.of("MAPPER","COMMON"),
                Arguments.of("MAPPER","PILOT"),
                Arguments.of("MAPPER","DCG"),
                Arguments.of("MAPPER","CLBR"),
                Arguments.of("OPTIMIZER","MODEL_TEST"),
                Arguments.of("OPTIMIZER","NEW_PRODUCT")

        );
    }



    @ParameterizedTest(name = "{index}) splitting={0}, template={1}")
    @MethodSource("splittingPointCodes")
    @DisplayName("Получить справочник точек сплиттования")
    void testChangeStatusExperimentById(String splitting,String template, RestService restService) {


        // 1. Формируем параметры
        DictionariesParams params = DictionariesParams.builder()
                .splittingPointCodes(List.of(splitting))
                .templateCodes(List.of(template))
                .build();

        // 2. Строим DTO через фабрику
        SplittingPointTemplateReqDto dto = factory.buildSplittingPointTemplateDto(params);


        var response = restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*")
                                .body(dto),
                        url + "/api/v1/dictionaries/splitting-point-templates")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));


    }





}

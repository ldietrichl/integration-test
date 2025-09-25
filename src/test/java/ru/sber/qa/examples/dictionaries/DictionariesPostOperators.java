package ru.sber.qa.examples.dictionaries;


import dto.dictionaries.request.OperatorsReqDto;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import request.dictionaries.DictionariesParams;
import request.dictionaries.DictionariesRequestFactory;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.services.db.DatabaseService;
import ru.sber.qa.services.rest.RestService;

import java.util.List;
import java.util.stream.Stream;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;


@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class DictionariesPostOperators {


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



    @Test
    @DisplayName("Получить справочник точек сплиттования")
    void testChangeStatusExperimentById(RestService restService) {


        // 1. Формируем параметры
        DictionariesParams params = DictionariesParams.builder()
                .operatorCodes(List.of("in","more_equal"))
                .build();

        // 2. Строим DTO через фабрику
        OperatorsReqDto dto = factory.buildOperatorsDto(params);


         restService.restClient()
                .post(spec -> spec
                                .config(P12_CONFIG)
                                .contentType(ContentType.JSON)
                                .accept("*/*")
                                .body(dto),
                        url + "/api/v1/dictionaries/operators")
                .should(
                        haveStatusCode(HttpStatus.SC_OK));

    }


    @Test
    //@Disabled
    @DisplayName("Получить справочник точек сплиттования")
    void testChangeStatusExperimentById1(DatabaseService dbService)  {

        //RestService restService = Environment.getForCurrentThread().getService(RestService.class);

        //restService.restClient().


        var x = dbService.dataBaseClient("dev_explab").executeSelect("select code from dictionaries.operator_dict od ").firstRow().toSimpleRow();
        System.out.println(x);

        Stream.of( x = dbService.dataBaseClient("dev_explab").executeSelect("select code from dictionaries.operator_dict od ").firstRow().toSimpleRow());

    }

}

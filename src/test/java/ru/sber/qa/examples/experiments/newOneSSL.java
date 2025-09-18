package ru.sber.qa.examples.experiments;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.services.rest.RestService;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class newOneSSL {

    String urlIFT = "http://ingress-http.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru/api/v1/message/audit";
    String urlDEV= "http://ingress-v2.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru";


    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                   .keyStore("src/test/resources/keystore.p12", "V_at_platform_2025")
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );



    @Test
    void testResponseValue(RestService restService) {



        step("Проверяем ответ на тестовый запрос", () -> {
            restService.restClient()
                    .get(spec ->
                                    spec

                                            .config(P12_CONFIG)
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*"),

                            "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru/api/v1/experiments?page=0&size=1&name=string&salt=string&exact=true&statuses=%5B%22DRAFT%22%5D")
                    .should(
                            haveStatusCode(HttpStatus.SC_OK));

                    /* .should(
                            // ищем ключ по JsonPath
                            notHaveJsonKey("result.locked", "Этого значения не должно быть"),
                            haveJsonKey("result", "Этот параметр должен быть в ответе"),
                            evaluateJsonPathExpressions(List.of(
                                    "result.current_time == 1732024955741",
                                    "status == 200")))

                     */
                    // фильтруется ответ по JsonPath, на выходе вложенная сущность, которую можно проверить на наличие ключей

        });
    }


}
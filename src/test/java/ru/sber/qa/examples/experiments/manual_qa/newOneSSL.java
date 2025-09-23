package ru.sber.qa.examples.experiments.manual_qa;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.feeders.ExperimentsFeeder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.examples.experiments.manual_qa.TestDataHelper.experimentId;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class newOneSSL {

    String urlIFT = "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru";
    String urlDEV= "https://ingress-v2.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru";
    String url= urlDEV;


    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                   .keyStore("src/test/resources/keystore.p12", "V_at_platform_2025")
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );



    @Test
    @Order(10)
    @DisplayName("Тест создания эксперимента")

    void testCreateExperiment(RestService restService) throws IOException {

        String filePath = "src/test/resources/experiments/create_exp.json";
        String jsonBody = new String(Files.readAllBytes(Paths.get(filePath)));

        String myId = ExperimentsFeeder.generateAqaMalilId();
        jsonBody = jsonBody.replace("${myId}", myId);


        String salt = ExperimentsFeeder.generateSalt();
        jsonBody = jsonBody.replace("${salt}", salt);

        long startDt = ExperimentsFeeder.startDt;
        jsonBody = jsonBody.replace("${startDt}", String.valueOf(startDt));

        long endDt = ExperimentsFeeder.endDt;
        jsonBody = jsonBody.replace("${endDt}", String.valueOf(endDt));


        String finalJsonBody = jsonBody;
        step("Проверяем ответ на тестовый запрос", () -> {
            var response = restService.restClient()
                    .post(spec ->
                                    spec

                                            .config(P12_CONFIG)
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*")
                                            .body(finalJsonBody),

                            url+"/api/v1/experiments")

                    .should(
                            haveStatusCode(HttpStatus.SC_OK));

            experimentId = response.toJsonPath().getLong("id");
            System.out.println("НОВЫЙ ИД: "+ experimentId);



            long experimentId = response.toJsonPath().getLong("id"); // Локальная переменная
            System.out.println("НОВЫЙ ИД: " + experimentId);


        });
    }


    @Test
    @Order(20)
    @DisplayName("Тест поиска эксперимента по id")
    void testFindExperimentById(RestService restService) {



        step("Проверяем ответ на тестовый запрос", () -> {
            restService.restClient()
                    .get(spec ->
                                    spec

                                            .config(P12_CONFIG)
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*"),

                            url+"/api/v1/experiments/"+ TestDataHelper.experimentId)
                    .should(
                            haveStatusCode(HttpStatus.SC_OK));

        });
    }


    @Test
    @Order(30)
    @DisplayName("Тест удаления эксперимента по id")
    void testDeleteExperimentById(RestService restService) {

        step("Проверяем ответ на тестовый запрос", () -> {
            restService.restClient()
                    .delete(spec ->
                                    spec

                                            .config(P12_CONFIG)
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*"),

                            url+"/api/v1/experiments/"+ TestDataHelper.experimentId)
                    .should(
                            haveStatusCode(HttpStatus.SC_OK));

        });
    }


}
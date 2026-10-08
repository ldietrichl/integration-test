package ru.sber.qa.experiments.manual_qa;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import config.environment.EnvironmentConfigurationExample;
import ru.sber.qa.services.rest.RestService;
import feeders.ExperimentsFeeder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.experiments.manual_qa.TestDataHelper.experimentId;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class newOneSSL {
    private static final String experimentsBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.EXPERIMENTS);
    // TLS is owned by the stand-level REST service configuration.


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

                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*")
                                            .body(finalJsonBody),

                            experimentsBaseUri+"/api/v1/experiments")

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

                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*"),

                            experimentsBaseUri+"/api/v1/experiments/"+ TestDataHelper.experimentId)
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

                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*"),

                            experimentsBaseUri+"/api/v1/experiments/"+ TestDataHelper.experimentId)
                    .should(
                            haveStatusCode(HttpStatus.SC_OK));

        });
    }


}

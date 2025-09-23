package ru.sber.qa.examples.experiments.manual_qa;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
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
public class newOne {

    String urlIFT = "http://ingress-http.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru/api/v1/message/audit";
    String urlDEV= "http://ingress-v2.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru";



    @Test
    void testResponseValue(RestService restService) {

        String requestBody = """
                {
                  "message": "string",
                  "params": [],
                  "changedParams": []
                }""";

        step("Проверяем ответ на тестовый запрос", () -> {
            restService.restClient()
                    .post(spec ->
                                    spec

                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*")
                                            .body(requestBody),

                            "http://ingress-http.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru/api/v1/message/audit")
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